# LazyLifecycle callbacks [ Documentation WIP ] 

Lazylifecycle callbacks is a simple framework to defer your non essential tasks, and initialisations out 
of the screen launch path while maintaining the same execution guarantees of android lifecycle callbacks.

## Fundamentals of lazy callbacks
 - First Draw : When the app is launched, play store tracks the COLD, WARM and HOT launch numbers. And it does so by measuring how fast your app is able to draw your first frame. App can start via launcher, notifications, deeplinks etc, and each could land the user in different screens. App is considered to have rendered its first screen when the "Displayed" marker is shown on the logcat. It always shown after all the upward callbacks such as onCreate, onStart, and onResume have returned.
 
 - So, any code that is executing in onCreate, onStart, and onResume, and other upward callbacks(not mentioning things like onPostResume) has potential to make the screen launch time bad. So, it is advisable to remove deferrable code away from android lifecycle callbacks.
 
 - But where should we move it? We can do things on demand, but not every thing can be moved ondemand. For example, you want to start making the n/w calls for fetching the images as soon as possible. Here, we do not want it to start while the screen's rendering is happening, but the moment screen renders with the placeholder view, we need to start the n/w-db calls. Suppose you want to load draft of a email from the db, first we would like to render the compose screen and then start fetching the draft.

## APIs
- activate() - activates lazy callbacks on an activity, usually done in onResume()
- deactivate() - deactivates lazy callbacks on an activity, usually done in onPause()
- watchedView - each screen has an unique view that is critical for that screen, and rendering on that screen can be considered as launch. If no watched view is provided or identified, decor view can be used.
- supportsLazyLifecycleCallbacks - This helps us onboard the framework incrementally, and enables us to conduct exeperiments to measure improvement. If this returns false for an activity, lazy lifecycle callbacks are not enabled for that screen. Each screen can override its value separately.
- LazyLifecycleCallbacks - Any activity/fragment that wants on onboard these callbacks have to implement this interface.

## The lazy callbacks provided by the framework 
- onLazyCreate - Executes once per activity/fragment just like onCreate, but after 1st draw on screen finishes or on expiry of timeout.
- onLazyStart - Excecutes if the activity resumes from a stopped state, but after 1st draw on screen finishes or on expiry of timeout.
- onLazyResume - Excecutes if the activity resumes from a paused state, but after 1st draw on screen finishes or on expiry of timeout.
- onViewCreatedLazy(view) - This is called only for fragments after 1st draw on screen finishes.

## Relative ordering of lazy callbacks
The order of these callbacks are maintained as per android. In an activity, onLazyCreate() will be followed by on LazyStart(), followed by onLazyResume().
In Fragments, onLazyCreate() will be followed by onViewCreatedLazy(view), followed by onLazyStart() followed by onLazyResume().
One thing to mind is, all the lazy callbacks happen after the triggers of the lazy lifecycle manager are satisfied. They do not interlace with the android lifecycle callbacks.

## How to setup
1. Implement `LazyLifecycleCallbacks` interface in the Activity(preferrebly a `BaseActivity`), and provide the default implementations of the method.
2. Create instance of LazyLifecycleManager in `onCreate()`.
3. Call `lifecycleManager.activate()` in `onResume`,
4. and call `lifecycleManager.deactivate()` in `onPause()`
5. Override `supportsLazyLifecycleCallbacks()` to `true`.
6. You are done. Other implementations of your `BaseActivity` can now override the lazy lifecycle callbacks and use it.

```abstract class BaseActivity : AppCompatActivity(), LazyLifecycleCallbacks {

    protected lateinit var lazyLifecycleManager: LazyLifecycleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lazyLifecycleManager = ViewBasedLazyLifecycleManager(this)
    }

    override fun onResume() {
        super.onResume()
        lazyLifecycleManager.activate()
    }

    override fun onPause() {
        super.onPause()
        lazyLifecycleManager.deactivate()
    }

    override fun onLazyCreate() {}

    override fun onLazyStart() {}

    override fun onLazyResume() {}

    override fun supportsLazyLifecycleCallbacks(): Boolean = false
}```

## Contributing

This project welcomes contributions and suggestions.  Most contributions require you to agree to a
Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us
the rights to use your contribution. For details, visit https://cla.opensource.microsoft.com.

When you submit a pull request, a CLA bot will automatically determine whether you need to provide
a CLA and decorate the PR appropriately (e.g., status check, comment). Simply follow the instructions
provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

## Trademarks

This project may contain trademarks or logos for projects, products, or services. Authorized use of Microsoft 
trademarks or logos is subject to and must follow 
[Microsoft's Trademark & Brand Guidelines](https://www.microsoft.com/en-us/legal/intellectualproperty/trademarks/usage/general).
Use of Microsoft trademarks or logos in modified versions of this project must not cause confusion or imply Microsoft sponsorship.
Any use of third-party trademarks or logos are subject to those third-party's policies.
