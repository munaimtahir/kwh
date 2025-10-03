# Hilt Test Runner Fix

## Issue
The issue (originally from PR #10 discussion r2401394874) was about using `Application()` directly creating an empty Application instance without proper initialization in instrumentation tests.

## Root Cause
The `testInstrumentationRunner` in `app/build.gradle.kts` was configured to use the default `androidx.test.runner.AndroidJUnitRunner`. This runner creates a basic Android Application instance without any Hilt initialization, which means:

1. Hilt dependency injection won't work in instrumentation tests
2. Tests that depend on `@HiltAndroidApp` annotated application won't have access to the dependency graph
3. This could lead to crashes or unexpected behavior in instrumentation tests

## Solution
Updated the `testInstrumentationRunner` configuration to use the custom `HiltTestRunner`:

```kotlin
testInstrumentationRunner = "com.example.kwh.HiltTestRunner"
```

## How It Works

### The Custom HiltTestRunner
Located at `app/src/androidTest/java/com/example/kwh/HiltTestRunner.kt`:

```kotlin
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

This custom runner:
1. Extends `AndroidJUnitRunner` to maintain all standard test runner functionality
2. Overrides `newApplication()` to instantiate `HiltTestApplication` instead of the default Application
3. `HiltTestApplication` is provided by Hilt's testing library and properly initializes all Hilt components for testing

## Benefits
- ✅ Instrumentation tests now have proper Hilt dependency injection support
- ✅ Tests can use `@HiltAndroidTest` annotation and inject dependencies
- ✅ Prevents "empty Application instance" issues mentioned in the original issue
- ✅ Follows Hilt testing best practices as documented in [Hilt Testing Guide](https://developer.android.com/training/dependency-injection/hilt-testing)

## Testing
This fix applies to instrumentation tests (located in `app/src/androidTest/`). The existing instrumentation test `HomeScreenTest` will now run with proper Hilt initialization.

Unit tests (located in `app/src/test/`) are not affected by this change as they use Robolectric and `ApplicationProvider.getApplicationContext()` which works independently.

## References
- [Hilt Testing Documentation](https://developer.android.com/training/dependency-injection/hilt-testing)
- [Hilt Test Application](https://dagger.dev/hilt/testing.html)
- Original Issue: PR #10 discussion r2401394874
