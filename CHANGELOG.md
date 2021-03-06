# 1.0 RC 7

API changes

* eval.polyglot: the parameter `source` was renamed to `code`

New features

* AWT based graphics devices (jpg, png, X11, ...) supported in native image
* Seamless way to create R data frames from Polyglot objects
  * Handled by as.data.frame.polyglot.value
  * Expected structure: KEYS are used as column names, the values must be homogenous arrays (e.g. respond to HAS_SIZE)

Bug fixes

* S3 dispatch works correctly with NULL
* Paths in eval.polyglot are resolved relative to the current working directory
* Connections: sockets can always be read and written, raw connections are always binary
* Promises are evaluated in LazyLoadDBFetch (to support delayedAssign)
* Fixed broken `Rscript --version`
* Various fixes necessary to pass dplyr tests (GitHub version of dplyr)

Bugfixes

# 1.0 RC 6

New features

* Support for reading/writing graphical parameters via par
  * in preparation for full graphics package support

Added missing R builtins and C API

* 'get' builtin supports the 'envir' argument
* 'inspect' internal
* SETLEVELS
* Rf_isObject
* SET_ENCLOS
* R_nchar
* R_forceAndCall

Bugfixes

* support for formulas that include '...'
* updating attributes of NULL produces empty list with given attributes
* treat CR/LF in readLine like GNU-R
* fix in La_chol (incorrect pivot attribute in return)
* various fixes in vector coercion code to produce GNU-R compatible warnings and errors

# 1.0 RC 5

New features

* Script that configures FastR for the current system (jre/languages/R/bin/configure_fastr)
  * allows to pass additional arguments for the underlying configure script
  * by default uses the following arguments: --with-x=no --with-aqua=no --enable-memory-profiling FFLAGS=-O2

Updates in interop

* R code evaluated via interop never returns a Java primitive type, but always a vector
* Vectors of size 1 that do not contain NA can be unboxed
* Sending the READ message to an atomic R vector (array subscript in most languages) gives
  * Java primitive type as long as the value is not `NA`
  * if the value is `NA`, a special value that responds to `IS_NULL` with `true`. If this value is passed back to R it behaves as `NA` again
* Note that sending the READ message to a list, environment, or other heterogenous data structure never gives atomic Java type but a primitive R vector

Bug fixes

* Various smaller issues discovered during testing of CRAN packages.

# 1.0 RC 4

# 1.0 RC 3

Added missing R builtins and C API

* vmmin
* SETLENGTH, TRUELENGHT, SET_TRUELENGTH
* simplified version of LEVELS
* addInputHandler, removeInputHandler

Bug fixes

* The plotting window did not display anything after it was closed and reopened.
* Various smaller issues discovered during testing of CRAN packages.

New features

* Script that configures FastR for the current system (jre/languages/R/bin/configure_fastr) does not require Autotools anymore.
* Users can build a native image of the FastR runtime. The native image provides faster startup and slightly slower peak performance. Run jre/languages/R/bin/install_r_native_image to build the image.
