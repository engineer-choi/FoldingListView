package com.liblary.foldinglistview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.ViewCompat
import java.util.*
/*
* cell.xml 파일에 쓰는 뷰.
* 커스텀 RelativeLayout임
* */

class FoldingCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {//커스텀 뷰 생성하고자 할 때 생성자에 받아야할 인자들

    /*
    전체 구조
    unfold(skipAnimation: Boolean)
    fold(skipAnimation: Boolean)
    toggle(skipAnimation: Boolean)
    prepareViewsForAnimation(
    -----------------------------------------------------
    viewHeights: ArrayList<Int>?,
    titleViewBitmap: Bitmap,
    contentViewBitmap: Bitmap
    ): ArrayList<FoldingCellView>
    -----------------------------------------------------
    calculateHeightsForAnimationParts(titleViewHeight: Int,
    contentViewHeight: Int,
    additionalFlipsCount: Int
    ): ArrayList<Int>
    -----------------------------------------------------
    createBackSideView(height: Int): ImageView
    createImageViewFromBitmap(bitmap: Bitmap): ImageView
    measureViewAndGetBitmap(view: View, parentWidth: Int): Bitmap
    createAndPrepareFoldingContainer(): LinearLayout
    -----------------------------------------------------
    startExpandHeightAnimation(viewHeights: ArrayList<Int>?, partAnimationDuration: Int)
    startCollapseHeightAnimation(viewHeights: ArrayList<Int>?, partAnimationDuration: Int)
    -----------------------------------------------------
    createAnimationChain(animationList: List<Animation>, animationObject: View)
    -----------------------------------------------------
    startFoldAnimation(\foldingCellElements: ArrayList<FoldingCellView>, foldingLayout: ViewGroup, part90degreeAnimationDuration: Int, animationEndListener: AnimationEndListener)
    startUnfoldAnimation(foldingCellElements: ArrayList<FoldingCellView>, foldingLayout: ViewGroup, part90degreeAnimationDuration: Int, animationEndListener: AnimationEndListener)
     */
    // state variables
    var isUnfolded: Boolean = false //펼쳐져있는가
    private var mAnimationInProgress: Boolean = false //애니메이션 진행중?

    // default values
    private val DEF_ANIMATION_DURATION = 1000
    private val DEF_BACK_SIDE_COLOR = Color.GRAY
    private val DEF_ADDITIONAL_FLIPS = 0
    private val DEF_CAMERA_HEIGHT = 30

    // current settings
    private var mAnimationDuration = DEF_ANIMATION_DURATION
    private var mBackSideColor = DEF_BACK_SIDE_COLOR
    private var mAdditionalFlipsCount = DEF_ADDITIONAL_FLIPS
    private var mCameraHeight = DEF_CAMERA_HEIGHT

    init {
        val styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.FoldingCell)//resource폴더의 attrs파일에 정의해둔 속성들을 사용하겠다는 말
        if (styledAttrs != null) {
            val count = styledAttrs.indexCount //정의해야할 속성의 개수 파악하여 리턴
            for (i in 0 until count) {//속성 정의 다할때까지 반복
                val attr = styledAttrs.getIndex(i)//속성들 중 i번째 인덱스를 attr로 지정.
                if (attr == R.styleable.FoldingCell_animationDuration) {//해당 인덱스 속성이 animationDuration인 경우
                    this.mAnimationDuration = styledAttrs.getInt(
                        R.styleable.FoldingCell_animationDuration,
                        DEF_ANIMATION_DURATION
                    )
                } else if (attr == R.styleable.FoldingCell_backSideColor) {
                    this.mBackSideColor = styledAttrs.getColor(
                        R.styleable.FoldingCell_backSideColor,
                        DEF_BACK_SIDE_COLOR
                    )
                } else if (attr == R.styleable.FoldingCell_additionalFlipsCount) {
                    this.mAdditionalFlipsCount = styledAttrs.getInt(
                        R.styleable.FoldingCell_additionalFlipsCount,
                        DEF_ADDITIONAL_FLIPS
                    )
                } else if (attr == R.styleable.FoldingCell_cameraHeight) {
                    this.mCameraHeight =
                        styledAttrs.getInt(R.styleable.FoldingCell_cameraHeight, DEF_CAMERA_HEIGHT)
                }
            }
            styledAttrs.recycle()
        }

        this.clipChildren = false
        this.clipToPadding = false
    }

    /**
     * Initializes folding cell programmatically with custom settings
     *
     * @param animationDuration    animation duration, default is 1000
     * @param backSideColor        color of back side, default is android.graphics.Color.GREY (0xFF888888)
     * @param additionalFlipsCount count of additional flips (after first one), set 0 for auto
     */
    fun initialize(animationDuration: Int, backSideColor: Int, additionalFlipsCount: Int) {
        this.mAnimationDuration = animationDuration
        this.mBackSideColor = backSideColor
        this.mAdditionalFlipsCount = additionalFlipsCount
    }

    /**
     * Initializes folding cell programmatically with custom settings
     *
     * @param animationDuration    animation duration, default is 1000
     * @param backSideColor        color of back side, default is android.graphics.Color.GREY (0xFF888888)
     * @param additionalFlipsCount count of additional flips (after first one), set 0 for auto
     */
    fun initialize(
        cameraHeight: Int,
        animationDuration: Int,
        backSideColor: Int,
        additionalFlipsCount: Int
    ) {
        this.mAnimationDuration = animationDuration
        this.mBackSideColor = backSideColor
        this.mAdditionalFlipsCount = additionalFlipsCount
        this.mCameraHeight = cameraHeight
    }

    /**
     * Unfold cell with (or without) animation
     *
     * @param skipAnimation if true - change state of cell instantly without animation
     */
    fun unfold(skipAnimation: Boolean) {//펼치기
        if (isUnfolded || mAnimationInProgress) return//펼쳐져있거나 애니메이션 진행중인경우 해당 메소드 실행 X

        // get main content parts
        val contentView = getChildAt(0) ?: return //getChildAt(index) 지정된 위치의 뷰를 반환. 없을경우 null 반환,  ?:는 엘비스 연산자로, 좌항이 null인 경우 우항을 디폴트로 실행
        val titleView = getChildAt(1) ?: return // FoldingCell이 감싸는 자식들의 순서가 인자에 해당함.

        // hide title and content views
        titleView.visibility = View.GONE
        contentView.visibility = View.GONE

        // Measure views and take a bitmaps to replace real views with images
        val bitmapFromTitleView = measureViewAndGetBitmap(titleView, this.measuredWidth)
        val bitmapFromContentView = measureViewAndGetBitmap(contentView, this.measuredWidth)

        if (skipAnimation) {
            contentView.visibility = View.VISIBLE
            this@FoldingCell.isUnfolded = true//펼쳐져있다고 표시
            this@FoldingCell.mAnimationInProgress = false
            this.layoutParams.height = contentView.height
        } else {
            ViewCompat.setHasTransientState(this, true)
            // create layout container for animation elements
            val foldingLayout = createAndPrepareFoldingContainer()
            this.addView(foldingLayout)
            // calculate heights of animation parts
            val heights = calculateHeightsForAnimationParts(
                titleView.height,
                contentView.height,
                mAdditionalFlipsCount
            )
            // create list with animation parts for animation
            val foldingCellElements =
                prepareViewsForAnimation(heights, bitmapFromTitleView, bitmapFromContentView)
            // start unfold animation with end listener
            val childCount = foldingCellElements.size
            val part90degreeAnimationDuration = mAnimationDuration / (childCount * 2)
            startUnfoldAnimation(
                foldingCellElements,
                foldingLayout,
                part90degreeAnimationDuration,
                object : AnimationEndListener() {
                    override fun onAnimationEnd(animation: Animation) {
                        contentView.visibility = View.VISIBLE
                        foldingLayout.visibility = View.GONE
                        this@FoldingCell.removeView(foldingLayout)
                        this@FoldingCell.isUnfolded = true//펼쳐짐
                        this@FoldingCell.mAnimationInProgress = false
                        ViewCompat.setHasTransientState(this@FoldingCell, true)
                    }
                })

            startExpandHeightAnimation(heights, part90degreeAnimationDuration * 2)
            this.mAnimationInProgress = true
        }
    }//펼치기

    /**
     * Fold cell with (or without) animation
     *
     * @param skipAnimation if true - change state of cell instantly without animation
     */
    fun fold(skipAnimation: Boolean) {
        if (!isUnfolded || mAnimationInProgress) return//접혀있을때나 애니메이션 진행중이라면 작동 X

        // get basic views
        val contentView = getChildAt(0) ?: return
        val titleView = getChildAt(1) ?: return

        // hide title and content views
        titleView.visibility = View.GONE
        contentView.visibility = View.GONE

        // make bitmaps from title and content views
        val bitmapFromTitleView = measureViewAndGetBitmap(titleView, this.measuredWidth)
        val bitmapFromContentView = measureViewAndGetBitmap(contentView, this.measuredWidth)

        if (skipAnimation) {
            contentView.visibility = View.GONE
            titleView.visibility = View.VISIBLE
            this@FoldingCell.mAnimationInProgress = false
            this@FoldingCell.isUnfolded = false//접혀있다고 표시
            this.layoutParams.height = titleView.height
        } else {
            ViewCompat.setHasTransientState(this, true)
            // create empty layout for folding animation
            val foldingLayout = createAndPrepareFoldingContainer()
            // add that layout to structure
            this.addView(foldingLayout)

            // calculate heights of animation parts
            val heights = calculateHeightsForAnimationParts(
                titleView.height,
                contentView.height,
                mAdditionalFlipsCount
            )
            // create list with animation parts for animation
            val foldingCellElements =
                prepareViewsForAnimation(heights, bitmapFromTitleView, bitmapFromContentView)
            val childCount = foldingCellElements.size
            val part90degreeAnimationDuration = mAnimationDuration / (childCount * 2)
            // start fold animation with end listener
            startFoldAnimation(
                foldingCellElements,
                foldingLayout,
                part90degreeAnimationDuration,
                object : AnimationEndListener() {
                    override fun onAnimationEnd(animation: Animation) {
                        contentView.visibility = View.GONE
                        titleView.visibility = View.VISIBLE
                        foldingLayout.visibility = View.GONE
                        this@FoldingCell.removeView(foldingLayout)
                        this@FoldingCell.mAnimationInProgress = false
                        this@FoldingCell.isUnfolded = false//접혀있다고 표시
                        ViewCompat.setHasTransientState(this@FoldingCell, true)
                    }
                })
            startCollapseHeightAnimation(heights, part90degreeAnimationDuration * 2)
            this.mAnimationInProgress = true
        }
    }//접기


    /**
     * Toggle current state of FoldingCellLayout
     */
    fun toggle(skipAnimation: Boolean) {//애니메이션 skip여부를 인자로 받는다
        if (this.isUnfolded) {//펼쳐져있다면
            this.fold(skipAnimation)
        } else {//접혀있다면
            this.unfold(skipAnimation)
            this.requestLayout()
        }
    }

    /**
     * Create and prepare list of FoldingCellViews with different bitmap parts for fold animation
     *
     * @param titleViewBitmap   bitmap from title view
     * @param contentViewBitmap bitmap from content view
     * @return list of FoldingCellViews with bitmap parts
     */
    protected fun prepareViewsForAnimation(viewHeights: ArrayList<Int>?, titleViewBitmap: Bitmap, contentViewBitmap: Bitmap): ArrayList<FoldingCellView> {
        check(!(viewHeights == null || viewHeights.isEmpty())) { "ViewHeights array must be not null and not empty" }

        val partsList = ArrayList<FoldingCellView>()!!

        val partWidth = titleViewBitmap.width
        var yOffset = 0
        for (i in viewHeights.indices) {
            val partHeight = viewHeights[i]
            val partBitmap = Bitmap.createBitmap(partWidth, partHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(partBitmap)
            val srcRect = Rect(0, yOffset, partWidth, yOffset + partHeight)
            val destRect = Rect(0, 0, partWidth, partHeight)
            canvas.drawBitmap(contentViewBitmap, srcRect, destRect, null)
            val backView = createImageViewFromBitmap(partBitmap)
            var frontView: ImageView? = null
            if (i < viewHeights.size - 1) {
                frontView =
                    if (i == 0) createImageViewFromBitmap(titleViewBitmap) else createBackSideView(
                        viewHeights[i + 1]
                    )
            }
            if(frontView==null){
                partsList.add(FoldingCellView(null, backView, context))
            }else{
                partsList.add(FoldingCellView(frontView as View, backView, context))
            }
            yOffset = yOffset + partHeight
        }

        return partsList
    }

    /**
     * Calculate heights for animation parts with some logic
     *
     * @param titleViewHeight      height of title view
     * @param contentViewHeight    height of content view
     * @param additionalFlipsCount count of additional flips (after first one), set 0 for auto
     * @return list of calculated heights
     */
    protected fun calculateHeightsForAnimationParts(titleViewHeight: Int, contentViewHeight: Int, additionalFlipsCount: Int): ArrayList<Int> {
        val partHeights = ArrayList<Int>()
        val additionalPartsTotalHeight = contentViewHeight - titleViewHeight * 2
        check(additionalPartsTotalHeight >= 0) { "Content View height is too small" }
        // add two main parts - guarantee first flip
        partHeights.add(titleViewHeight)
        partHeights.add(titleViewHeight)

        // if no space left - return
        if (additionalPartsTotalHeight == 0)
            return partHeights

        // if some space remained - use two different logic
        if (additionalFlipsCount != 0) {
            // 1 - additional parts count is specified and it is not 0 - divide remained space
            val additionalPartHeight = additionalPartsTotalHeight / additionalFlipsCount
            val remainingHeight = additionalPartsTotalHeight % additionalFlipsCount

            check(additionalPartHeight + remainingHeight <= titleViewHeight) { "Additional flips count is too small" }
            for (i in 0 until additionalFlipsCount)
                partHeights.add(additionalPartHeight + if (i == 0) remainingHeight else 0)
        } else {
            // 2 - additional parts count isn't specified or 0 - divide remained space to parts with title view size
            val partsCount = additionalPartsTotalHeight / titleViewHeight
            val restPartHeight = additionalPartsTotalHeight % titleViewHeight
            for (i in 0 until partsCount)
                partHeights.add(titleViewHeight)
            if (restPartHeight > 0)
                partHeights.add(restPartHeight)
        }

        return partHeights
    }

    /**
     * Create image view for display back side of flip view
     *
     * @param height height for view
     * @return ImageView with selected height and default background color
     */
    protected fun createBackSideView(height: Int): ImageView {
        val imageView = ImageView(context)
        imageView.setBackgroundColor(mBackSideColor)
        imageView.layoutParams =
            RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        return imageView
    }

    /**
     * Create image view for display selected bitmap
     *
     * @param bitmap bitmap to display in image view
     * @return ImageView with selected bitmap
     */
    protected fun createImageViewFromBitmap(bitmap: Bitmap): ImageView {
        val imageView = ImageView(context)
        imageView.setImageBitmap(bitmap)
        imageView.layoutParams = RelativeLayout.LayoutParams(bitmap.width, bitmap.height)
        return imageView
    }

    /**
     * Create bitmap from specified View with specified with
     *
     * @param view        source for bitmap
     * @param parentWidth result bitmap width
     * @return bitmap from specified view
     */
    protected fun measureViewAndGetBitmap(view: View, parentWidth: Int): Bitmap {
        val specW = View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY)
        val specH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(specW, specH)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.translate((-view.scrollX).toFloat(), (-view.scrollY).toFloat())
        view.draw(c)
        return b
    }

    /**
     * Create layout that will be a container for animation elements
     *
     * @return Configured container for animation elements (LinearLayout)
     */
    protected fun createAndPrepareFoldingContainer(): LinearLayout {
        val foldingContainer = LinearLayout(context)
        foldingContainer.clipToPadding = false
        foldingContainer.clipChildren = false
        foldingContainer.orientation = LinearLayout.VERTICAL
        foldingContainer.layoutParams =
            LinearLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
        return foldingContainer
    }

    /**
     * Prepare and start height expand animation for FoldingCellLayout
     *
     * @param partAnimationDuration one part animate duration
     * @param viewHeights           heights of animation parts
     */
    protected fun startExpandHeightAnimation(
        viewHeights: ArrayList<Int>?,
        partAnimationDuration: Int
    ) {
        require(!(viewHeights == null || viewHeights.isEmpty())) { "ViewHeights array must have at least 2 elements" }

        val heightAnimations = ArrayList<Animation>()
        var fromHeight = viewHeights[0]
        val delay = 0
        val animationDuration = partAnimationDuration - delay
        for (i in 1 until viewHeights.size) {
            val toHeight = fromHeight + viewHeights[i]
            val heightAnimation = HeightAnimation(this, fromHeight, toHeight, animationDuration)
                .withInterpolator(DecelerateInterpolator())
            heightAnimation.setStartOffset(delay.toLong())
            heightAnimations.add(heightAnimation)
            fromHeight = toHeight
        }
        createAnimationChain(heightAnimations, this)
        this.startAnimation(heightAnimations[0])
    }

    /**
     * Prepare and start height collapse animation for FoldingCellLayout
     *
     * @param partAnimationDuration one part animate duration
     * @param viewHeights           heights of animation parts
     */
    protected fun startCollapseHeightAnimation(
        viewHeights: ArrayList<Int>?,
        partAnimationDuration: Int
    ) {
        require(!(viewHeights == null || viewHeights.isEmpty())) { "ViewHeights array must have at least 2 elements" }

        val heightAnimations = ArrayList<Animation>()
        var fromHeight = viewHeights[0]
        for (i in 1 until viewHeights.size) {
            val toHeight = fromHeight + viewHeights[i]
            heightAnimations.add(
                HeightAnimation(this, toHeight, fromHeight, partAnimationDuration)
                    .withInterpolator(DecelerateInterpolator())
            )
            fromHeight = toHeight
        }

        Collections.reverse(heightAnimations)
        createAnimationChain(heightAnimations, this)
        this.startAnimation(heightAnimations[0])
    }

    /**
     * Create "animation chain" for selected view from list of animation objects
     *
     * @param animationList   collection with animations
     * @param animationObject view for animations
     */
    protected fun createAnimationChain(animationList: List<Animation>, animationObject: View) {
        for (i in animationList.indices) {
            val animation = animationList[i]
            if (i + 1 < animationList.size) {
                animation.setAnimationListener(object : AnimationEndListener() {
                    override fun onAnimationEnd(animation: Animation) {
                        animationObject.startAnimation(animationList[i + 1])
                    }
                })
            }
        }
    }

    /**
     * Start fold animation
     *
     * @param foldingCellElements           ordered list with animation parts from top to bottom
     * @param foldingLayout                 prepared layout for animation parts
     * @param part90degreeAnimationDuration animation duration for 90 degree rotation
     * @param animationEndListener          animation end callback
     */
    protected fun startFoldAnimation(
        foldingCellElements: ArrayList<FoldingCellView>, foldingLayout: ViewGroup,
        part90degreeAnimationDuration: Int, animationEndListener: AnimationEndListener
    ) {
        for (foldingCellElement in foldingCellElements)
            foldingLayout.addView(foldingCellElement)

        Collections.reverse(foldingCellElements)

        var nextDelay = 0
        for (i in foldingCellElements.indices) {
            val cell = foldingCellElements[i]
            cell.visibility = View.VISIBLE
            // not FIRST(BOTTOM) element - animate front view
            if (i != 0) {
                val foldAnimation = FoldAnimation(
                    FoldAnimation.FoldAnimationMode.UNFOLD_UP,
                    mCameraHeight,
                    part90degreeAnimationDuration.toLong()
                )
                    .withStartOffset(nextDelay)
                    .withInterpolator(DecelerateInterpolator())
                // if last(top) element - add end listener
                if (i == foldingCellElements.size - 1) {
                    foldAnimation.setAnimationListener(animationEndListener)
                }
                cell.animateFrontView(foldAnimation)
                nextDelay = nextDelay + part90degreeAnimationDuration
            }
            // if not last(top) element - animate whole view
            if (i != foldingCellElements.size - 1) {
                cell.startAnimation(
                    FoldAnimation(
                        FoldAnimation.FoldAnimationMode.FOLD_UP,
                        mCameraHeight,
                        part90degreeAnimationDuration.toLong()
                    )
                        .withStartOffset(nextDelay)
                        .withInterpolator(DecelerateInterpolator())
                )
                nextDelay = nextDelay + part90degreeAnimationDuration
            }
        }
    }

    /**
     * Start unfold animation
     *
     * @param foldingCellElements           ordered list with animation parts from top to bottom
     * @param foldingLayout                 prepared layout for animation parts
     * @param part90degreeAnimationDuration animation duration for 90 degree rotation
     * @param animationEndListener          animation end callback
     */
    protected fun startUnfoldAnimation(
        foldingCellElements: ArrayList<FoldingCellView>, foldingLayout: ViewGroup,
        part90degreeAnimationDuration: Int, animationEndListener: AnimationEndListener
    ) {
        var nextDelay = 0
        for (i in foldingCellElements.indices) {
            val cell = foldingCellElements[i]
            cell.visibility = View.VISIBLE
            foldingLayout.addView(cell)
            // if not first(top) element - animate whole view
            if (i != 0) {
                val foldAnimation = FoldAnimation(
                    FoldAnimation.FoldAnimationMode.UNFOLD_DOWN,
                    mCameraHeight,
                    part90degreeAnimationDuration.toLong()
                )
                    .withStartOffset(nextDelay)
                    .withInterpolator(DecelerateInterpolator())

                // if last(bottom) element - add end listener
                if (i == foldingCellElements.size - 1) {
                    foldAnimation.setAnimationListener(animationEndListener)
                }

                nextDelay = nextDelay + part90degreeAnimationDuration
                cell.startAnimation(foldAnimation)

            }
            // not last(bottom) element - animate front view
            if (i != foldingCellElements.size - 1) {
                cell.animateFrontView(
                    FoldAnimation(
                        FoldAnimation.FoldAnimationMode.FOLD_DOWN,
                        mCameraHeight,
                        part90degreeAnimationDuration.toLong()
                    )
                        .withStartOffset(nextDelay)
                        .withInterpolator(DecelerateInterpolator())
                )
                nextDelay = nextDelay + part90degreeAnimationDuration
            }
        }
    }
}
