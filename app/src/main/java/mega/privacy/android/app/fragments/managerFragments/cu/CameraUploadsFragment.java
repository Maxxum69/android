package mega.privacy.android.app.fragments.managerFragments.cu;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.ListenScrollChangesHelper;
import mega.privacy.android.app.databinding.FragmentCameraUploadsBinding;
import mega.privacy.android.app.databinding.FragmentCameraUploadsFirstLoginBinding;
import mega.privacy.android.app.fragments.BaseFragment;
import mega.privacy.android.app.globalmanagement.SortOrderManagement;
import mega.privacy.android.app.lollipop.FullScreenImageViewerLollipop;
import mega.privacy.android.app.lollipop.ManagerActivityLollipop;
import mega.privacy.android.app.repo.MegaNodeRepo;
import mega.privacy.android.app.utils.StringResourcesUtils;
import nz.mega.sdk.MegaNode;

import static mega.privacy.android.app.components.dragger.DragToExitSupport.observeDragSupportEvents;
import static mega.privacy.android.app.components.dragger.DragToExitSupport.putThumbnailLocation;
import static mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ADAPTER_TYPE;
import static mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_HANDLE;
import static mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ORDER_GET_CHILDREN;
import static mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_PARENT_NODE_HANDLE;
import static mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_POSITION;
import static mega.privacy.android.app.utils.Constants.MIN_ITEMS_SCROLLBAR;
import static mega.privacy.android.app.utils.Constants.MIN_ITEMS_SCROLLBAR_GRID;
import static mega.privacy.android.app.utils.Constants.PHOTO_SYNC_ADAPTER;
import static mega.privacy.android.app.utils.Constants.REQUEST_CAMERA_ON_OFF;
import static mega.privacy.android.app.utils.Constants.REQUEST_CAMERA_ON_OFF_FIRST_TIME;
import static mega.privacy.android.app.utils.Constants.SCROLLING_UP_DIRECTION;
import static mega.privacy.android.app.utils.Constants.VIEWER_FROM_CUMU;
import static mega.privacy.android.app.utils.JobUtil.startCameraUploadService;
import static mega.privacy.android.app.utils.LogUtil.logDebug;
import static mega.privacy.android.app.utils.PermissionUtils.hasPermissions;
import static mega.privacy.android.app.utils.StyleUtils.setTextStyle;
import static mega.privacy.android.app.utils.TextUtil.formatEmptyScreenText;
import static mega.privacy.android.app.utils.Util.showSnackbar;
import static nz.mega.sdk.MegaApiJava.INVALID_HANDLE;

@AndroidEntryPoint
public class CameraUploadsFragment extends BaseFragment implements CUGridViewAdapter.Listener {

    public static final int ALL_VIEW = 0;
    public static final int DAYS_VIEW = 1;
    public static final int MONTHS_VIEW = 2;
    public static final int YEARS_VIEW = 3;

    // Cards per row
    private static final int SPAN_CARD_PORTRAIT = 1;
    private static final int SPAN_CARD_LANDSCAPE = 2;

    // Thumbnails per row
    private static final int SPAN_LARGE_GRID = 3;
    private static final int SPAN_SMALL_GRID_PORTRAIT = 5;
    private static final int SPAN_SMALL_GRID_LANDSCAPE = 7;

    @Inject
    SortOrderManagement sortOrderManagement;

    private ManagerActivityLollipop mManagerActivity;
    private FragmentCameraUploadsFirstLoginBinding mFirstLoginBinding;
    private FragmentCameraUploadsBinding binding;
    private CUGridViewAdapter gridAdapter;
    private CUCardViewAdapter cardAdapter;
    private ActionMode mActionMode;

    private CuViewModel viewModel;

    private int selectedView = ALL_VIEW;

    private static final String AD_SLOT = "and3";

    public int getItemCount() {
        return gridAdapter == null ? 0 : gridAdapter.getItemCount();
    }

    public void reloadNodes() {
        viewModel.loadCuNodes();
    }

    public void checkScroll() {
        if (viewModel == null || binding == null) {
            return;
        }

        mManagerActivity.changeAppBarElevation(viewModel.isSelecting()
                || binding.cuList.canScrollVertically(SCROLLING_UP_DIRECTION));
    }

    public void selectAll() {
        viewModel.selectAll();
    }

    public int onBackPressed() {
        if (mManagerActivity.isFirstNavigationLevel()) {
            return 0;
        } else if (isEnableCUFragmentShown()) {
            skipCUSetup();
            return 1;
        } else {
            reloadNodes();
            mManagerActivity.invalidateOptionsMenu();
            mManagerActivity.setToolbarTitle();
            return 1;
        }
    }

    public void onStoragePermissionRefused() {
        showSnackbar(context, getString(R.string.on_refuse_storage_permission));
        skipCUSetup();
    }

    private void skipCUSetup() {
        viewModel.setEnableCUShown(false);
        viewModel.setCamSyncEnabled(false);
        mManagerActivity.setFirstNavigationLevel(false);

        if (mManagerActivity.isFirstLogin()) {
            mManagerActivity.skipInitialCUSetup();
        } else {
            mManagerActivity.refreshCameraUpload();
        }
    }

    private void requestCameraUploadPermission(String[] permissions, int requestCode) {
        ActivityCompat.requestPermissions(mManagerActivity, permissions,
                requestCode);
    }

    public void enableCu() {
        if (mFirstLoginBinding == null) {
            return;
        }

        viewModel.enableCu(mFirstLoginBinding.cellularConnectionSwitch.isChecked(),
                mFirstLoginBinding.uploadVideosSwitch.isChecked());

        mManagerActivity.setFirstLogin(false);
        viewModel.setEnableCUShown(false);
        startCU();
    }

    private void startCU() {
        mManagerActivity.refreshCameraUpload();

        new Handler().postDelayed(() -> {
            logDebug("Starting CU");
            startCameraUploadService(context);
        }, 1000);
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mManagerActivity = (ManagerActivityLollipop) context;

        CuViewModelFactory viewModelFactory =
                new CuViewModelFactory(megaApi, DatabaseHandler.getDbHandler(context),
                        new MegaNodeRepo(megaApi, dbH), context, sortOrderManagement);
        viewModel = new ViewModelProvider(this, viewModelFactory).get(CuViewModel.class);

        initAdsLoader(AD_SLOT, true);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        if (mManagerActivity.getFirstLogin() || viewModel.isEnableCUShown()) {
            viewModel.setEnableCUShown(true);
            return createCameraUploadsViewForFirstLogin(inflater, container);
        } else {
            binding = FragmentCameraUploadsBinding.inflate(inflater, container, false);
            setupGoogleAds();
            return binding.getRoot();
        }
    }

    private View createCameraUploadsViewForFirstLogin(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container) {
        viewModel.setInitialPreferences();

        mFirstLoginBinding =
                FragmentCameraUploadsFirstLoginBinding.inflate(inflater, container, false);

        new ListenScrollChangesHelper().addViewToListen(mFirstLoginBinding.camSyncScrollView,
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> mManagerActivity
                        .changeAppBarElevation(mFirstLoginBinding.camSyncScrollView.canScrollVertically(SCROLLING_UP_DIRECTION)));

        mFirstLoginBinding.enableButton.setOnClickListener(v -> {
            MegaApplication.getInstance().sendSignalPresenceActivity();
            String[] permissions = { android.Manifest.permission.READ_EXTERNAL_STORAGE };
            if (hasPermissions(context, permissions)) {
                mManagerActivity.checkIfShouldShowBusinessCUAlert();
            } else {
                requestCameraUploadPermission(permissions, REQUEST_CAMERA_ON_OFF_FIRST_TIME);
            }
        });

        return mFirstLoginBinding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (binding == null) {
            return;
        }

        setupRecyclerView();
        setupOtherViews();
        observeLiveData();
    }

    /**
     * Set the Ads view container to the Ads Loader
     */
    private void setupGoogleAds() {
        mAdsLoader.setAdViewContainer(binding.adViewContainer,
                mManagerActivity.getOutMetrics());
    }

    private void setupRecyclerView() {
        binding.cuList.setHasFixedSize(true);
        binding.cuList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkScroll();
            }
        });

        setGridView();
    }

    private void setGridView() {
        boolean smallGrid = mManagerActivity.isSmallGridCameraUploads;
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        int spanCount = getSpanCount(isPortrait, smallGrid);
        GridLayoutManager layoutManager = new GridLayoutManager(context, spanCount);
        binding.cuList.setLayoutManager(layoutManager);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) binding.cuList.getLayoutParams();

        if (selectedView == ALL_VIEW) {
            int imageMargin = getResources().getDimensionPixelSize(smallGrid
                    ? R.dimen.cu_fragment_image_margin_small
                    : R.dimen.cu_fragment_image_margin_large);

            int gridWidth = ((outMetrics.widthPixels - imageMargin * spanCount * 2) - imageMargin * 2) / spanCount;
            int icSelectedWidth = getResources().getDimensionPixelSize(smallGrid
                    ? R.dimen.cu_fragment_ic_selected_size_small
                    : R.dimen.cu_fragment_ic_selected_size_large);

            int icSelectedMargin = getResources().getDimensionPixelSize(smallGrid
                    ? R.dimen.cu_fragment_ic_selected_margin_small
                    : R.dimen.cu_fragment_ic_selected_margin_large);

            CuItemSizeConfig itemSizeConfig = new CuItemSizeConfig(smallGrid, gridWidth,
                    icSelectedWidth, imageMargin,
                    getResources().getDimensionPixelSize(R.dimen.cu_fragment_selected_padding),
                    icSelectedMargin,
                    getResources().getDimensionPixelSize(
                            R.dimen.cu_fragment_selected_round_corner_radius));

            gridAdapter = new CUGridViewAdapter(this, spanCount, itemSizeConfig);
            gridAdapter.setHasStableIds(true);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int position) {
                    return gridAdapter.getSpanSize(position);
                }
            });
            binding.cuList.setAdapter(gridAdapter);
            params.leftMargin = params.rightMargin = imageMargin;
        } else {
            int cardMargin = getResources().getDimensionPixelSize(isPortrait
                    ? R.dimen.card_margin_portrait
                    : R.dimen.card_margin_landscape);

            int cardWidth = ((outMetrics.widthPixels - cardMargin * spanCount * 2) - cardMargin * 2) / spanCount;

            cardAdapter = new CUCardViewAdapter(selectedView, cardWidth, cardMargin);
            cardAdapter.setHasStableIds(true);
            binding.cuList.setAdapter(cardAdapter);
            params.leftMargin = params.rightMargin = cardMargin;
        }

        binding.cuList.setLayoutParams(params);
        binding.scroller.setRecyclerView(binding.cuList);
    }

    private int getSpanCount(boolean isPortrait, boolean smallGRid) {
        if (selectedView != ALL_VIEW) {
            return isPortrait ? SPAN_CARD_PORTRAIT : SPAN_CARD_LANDSCAPE;
        } else if (smallGRid) {
            return isPortrait ? SPAN_SMALL_GRID_PORTRAIT : SPAN_SMALL_GRID_LANDSCAPE;
        } else {
            return SPAN_LARGE_GRID;
        }
    }

    private void setupOtherViews() {
        binding.emptyEnableCuButton.setOnClickListener(v -> enableCUClick());
        binding.emptyHintText.setText(HtmlCompat.fromHtml(
                formatEmptyScreenText(context, StringResourcesUtils.getString(R.string.photos_empty)),
                HtmlCompat.FROM_HTML_MODE_LEGACY));

        binding.allButton.setOnClickListener(v -> newViewClicked(ALL_VIEW));
        binding.daysButton.setOnClickListener(v -> newViewClicked(DAYS_VIEW));
        binding.monthsButton.setOnClickListener(v -> newViewClicked(MONTHS_VIEW));
        binding.yearsButton.setOnClickListener(v -> newViewClicked(YEARS_VIEW));
        updateViewSelected();
    }

    private void newViewClicked(int selectedView) {
        if (this.selectedView == selectedView) {
            return;
        }

        this.selectedView = selectedView;
        setGridView();

        switch (selectedView) {
            case DAYS_VIEW:
                showDayCards(viewModel.getDayCards());
                break;

            case MONTHS_VIEW:
                showMonthCards(viewModel.getMonthCards());
                break;

            case YEARS_VIEW:
                showYearCards(viewModel.getYearCards());
                break;

            default:
                gridAdapter.setNodes(viewModel.getCUNodes());
        }

        updateViewSelected();
    }

    public void enableCUClick() {
        ((MegaApplication) ((Activity) context).getApplication()).sendSignalPresenceActivity();
        String[] permissions = { android.Manifest.permission.READ_EXTERNAL_STORAGE };

        if (hasPermissions(context, permissions)) {
            viewModel.setEnableCUShown(true);
            mManagerActivity.refreshCameraUpload();
        } else {
            requestCameraUploadPermission(permissions, REQUEST_CAMERA_ON_OFF);
        }
    }

    private void observeLiveData() {
        viewModel.cuNodes().observe(getViewLifecycleOwner(), nodes -> {
            if (!isResumed()) {
                return;
            }

            boolean showScroller = nodes.size() >= (mManagerActivity.isSmallGridCameraUploads
                    ? MIN_ITEMS_SCROLLBAR_GRID : MIN_ITEMS_SCROLLBAR);
            binding.scroller.setVisibility(showScroller ? View.VISIBLE : View.GONE);
            gridAdapter.setNodes(nodes);
            updateEnableCUButtons(viewModel.isCUEnabled());
            mManagerActivity.updateCuFragmentOptionsMenu();

            binding.emptyHint.setVisibility(nodes.isEmpty() ? View.VISIBLE : View.GONE);
            binding.cuList.setVisibility(nodes.isEmpty() ? View.GONE : View.VISIBLE);
            binding.scroller.setVisibility(nodes.isEmpty() ? View.GONE : View.VISIBLE);
            binding.viewTypeLayout.setVisibility(nodes.isEmpty() ? View.GONE : View.VISIBLE);
        });

        viewModel.nodeToOpen()
                .observe(getViewLifecycleOwner(), pair -> openNode(pair.first, pair.second));

        viewModel.nodeToAnimate().observe(getViewLifecycleOwner(), pair -> {
            if (pair.first < 0 || pair.first >= gridAdapter.getItemCount()) {
                return;
            }

            gridAdapter.showSelectionAnimation(pair.first, pair.second,
                    binding.cuList.findViewHolderForLayoutPosition(pair.first));
        });

        viewModel.actionBarTitle().observe(getViewLifecycleOwner(), title -> {
            ActionBar actionBar = ((AppCompatActivity) context).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
            }
        });

        viewModel.actionMode().observe(getViewLifecycleOwner(), visible -> {
            if (visible) {
                if (mActionMode == null) {
                    mActionMode = ((AppCompatActivity) context).startSupportActionMode(
                            new CuActionModeCallback(context, this, viewModel, megaApi));
                }

                mActionMode.setTitle(String.valueOf(viewModel.getSelectedNodesCount()));
                mActionMode.invalidate();
            } else {
                if (mActionMode != null) {
                    mActionMode.finish();
                    mActionMode = null;
                }
            }
        });

        viewModel.camSyncEnabled().observe(getViewLifecycleOwner(), this::updateEnableCUButtons);
        observeDragSupportEvents(getViewLifecycleOwner(), binding.cuList, VIEWER_FROM_CUMU);

        viewModel.getDayCardsData().observe(getViewLifecycleOwner(), this::showDayCards);
        viewModel.getMonthCardsData().observe(getViewLifecycleOwner(), this::showMonthCards);
        viewModel.getYearCardsData().observe(getViewLifecycleOwner(), this::showYearCards);
    }

    private void updateEnableCUButtons(boolean cuEnabled) {
        boolean emptyAdapter = gridAdapter.getItemCount() <= 0;
        mManagerActivity.updateEnableCuButton(!cuEnabled && !emptyAdapter ? View.VISIBLE : View.GONE);
        binding.emptyEnableCuButton.setVisibility(!cuEnabled && emptyAdapter ? View.VISIBLE : View.GONE);
    }

    private void showDayCards(List<Pair<CUCard, CuNode>> dayCards) {
        if (selectedView == DAYS_VIEW) {
            cardAdapter.setCards(dayCards);
        }
    }

    private void showMonthCards(List<Pair<CUCard, CuNode>> monthCards) {
        if (selectedView == MONTHS_VIEW) {
             cardAdapter.setCards(monthCards);
        }
    }

    private void showYearCards(List<Pair<CUCard, CuNode>> yearCards) {
        if (selectedView == YEARS_VIEW) {
            cardAdapter.setCards(yearCards);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CAMERA_ON_OFF:
            case REQUEST_CAMERA_ON_OFF_FIRST_TIME: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mManagerActivity.checkIfShouldShowBusinessCUAlert();
                }
                break;
            }
        }
    }

    @Override public void onResume() {
        super.onResume();

        reloadNodes();
    }

    private void openNode(int position, CuNode cuNode) {
        if (position < 0 || position >= gridAdapter.getItemCount()) {
            return;
        }

        MegaNode node = cuNode.getNode();
        if (node == null) {
            return;
        }

        MegaNode parentNode = megaApi.getParentNode(node);
        Intent intent = new Intent(context, FullScreenImageViewerLollipop.class)
                .putExtra(INTENT_EXTRA_KEY_POSITION, cuNode.getIndexForViewer())
                .putExtra(INTENT_EXTRA_KEY_ORDER_GET_CHILDREN, sortOrderManagement.getOrderCamera())
                .putExtra(INTENT_EXTRA_KEY_HANDLE, node.getHandle())
                .putExtra(INTENT_EXTRA_KEY_PARENT_NODE_HANDLE,
                        parentNode == null || parentNode.getType() == MegaNode.TYPE_ROOT
                                ? INVALID_HANDLE
                                : parentNode.getHandle())
                .putExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, PHOTO_SYNC_ADAPTER);

        putThumbnailLocation(intent, binding.cuList, position, VIEWER_FROM_CUMU, gridAdapter);
        startActivity(intent);
        requireActivity().overridePendingTransition(0, 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override public void onNodeClicked(int position, CuNode node) {
        viewModel.onNodeClicked(position, node);
    }

    @Override public void onNodeLongClicked(int position, CuNode node) {
        viewModel.onNodeLongClicked(position, node);
    }

    public boolean isEnableCUFragmentShown() {
        return viewModel.isEnableCUShown();
    }

    private void updateViewSelected() {
        setViewTypeButtonStyle(binding.allButton, false);
        setViewTypeButtonStyle(binding.daysButton, false);
        setViewTypeButtonStyle(binding.monthsButton, false);
        setViewTypeButtonStyle(binding.yearsButton, false);

        switch (selectedView) {
            case DAYS_VIEW:
                setViewTypeButtonStyle(binding.daysButton, true);
                break;

            case MONTHS_VIEW:
                setViewTypeButtonStyle(binding.monthsButton, true);
                break;

            case YEARS_VIEW:
                setViewTypeButtonStyle(binding.yearsButton, true);
                break;

            default:
                setViewTypeButtonStyle(binding.allButton, true);
        }
    }

    private void setViewTypeButtonStyle(TextView textView, boolean enabled) {
        textView.setBackgroundResource(enabled
                ? R.drawable.background_18dp_rounded_selected_button
                : R.drawable.background_18dp_rounded_unselected_button);

        setTextStyle(context, textView, enabled
                        ? R.style.TextAppearance_Mega_Subtitle2_Medium_WhiteGrey87
                        : R.style.TextAppearance_Mega_Subtitle2_Normal_Grey87White87,
                enabled ? R.color.white_grey_087 : R.color.grey_087_white_087, false);
    }
}
