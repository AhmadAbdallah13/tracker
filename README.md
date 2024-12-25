# To debug while running on an emulator:
chrome://inspect/#devices

# Update the android configuration:
ionic build && npx cap sync android
or
cd android && ./gradlew clean && ./gradlew build && cd .. && npx cap sync android

# Live reload on the emultor
ionic build && ionic capacitor run android -l --external


# run:
npx cap run android
