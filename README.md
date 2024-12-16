to debug while running on an emulator: chrome://inspect/#devices 

update the android configuration:
1. ionic build
2. npx cap sync android
or
1. cd android
2. ./gradlew clean
3. ./gradlew build
4. cd ..
5. npx cap sync android


run: npx cap run android
