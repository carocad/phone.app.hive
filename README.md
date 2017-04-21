# hive

A Clojure library designed to ... well, that part is up to you.

## Usage

FIXME

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
  
## Getting Started
This project uses re-natal which in turn wraps react-native (v0.41.1). You would need to follow their installation instruction: 
- [re-natal](https://github.com/drapanjanas/re-natal)
- [react-native](https://facebook.github.io/react-native/docs/getting-started.html)

Once you have installed all the dependencies, run `lein prod-build` and then:
- for development: `react-native run-android`. You should also check the instructions in *re-natal* regarding development in devices/emulators.
- for debug/test version: `react-native bundle --dev false --platform android --entry-file index.android.js --bundle-output ./android/app/build/intermediates/assets/debug/index.android.bundle --assets-dest ./android/app/build/intermediates/res/merged/debug`. This will create an *apk* file which you can then install on your device without needing a connection to react-native development server.
- for production - not supporter yet :(

### NOTES
- you need to provide a `secrets.clj` file with
  - mapbox api key
  - firebase config
- you need to provide a `google-services.json` file downloadable from the firebase project
- If you want to run *figwheel* in a Cursive repl you need to follow the instructions [here](https://github.com/bhauman/lein-figwheel/wiki/Running-figwheel-in-a-Cursive-Clojure-REPL)
