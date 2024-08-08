# ImpairCheck

ImpairCheck is a mobile application designed to initially detect the impaired level of users using AI models. The app leverages face detection, calculates the percentage of bloodshot eyes, ensures the user can stand on one leg for at least 10-15 seconds using video camera body movements analysis, and chats with a bot trained to ask certain questions to determine if the user is under the influence of drugs. The app opens the camera while the user is answering questions to prevent cheating. This solution is ideal for bus drivers, pilots, truck drivers, construction workers, and factory workers, helping to reduce the number of deaths and injuries by preventing work in an impaired state.

## Features

- **Face Detection**: Utilizes AI models to detect the user's face and calculate the percentage of bloodshot eyes.
- **Pose Detection**: Ensures the user can stand on one leg for 10-15 seconds using video camera body movements analysis.
- **Chatbot Interaction**: A bot trained with Google AI Studio asks questions to determine drug influence, with the camera active to prevent cheating.
- **Data Review**: All collected data from the tests are reviewed by experts to decide if the user needs to see a doctor or if they are fit to continue working.
- **Safety and Prevention**: Aims to reduce deaths and injuries in critical environments such as buses, airplanes, construction sites, and petrol stations.
- **Slogan**: "Do the test, save more lives."

## Technology Stack

- **Android Development**: Implemented using Kotlin.
- **Face Recognition and Pose Landmarks**: AI models to detect face and body movements.
- **Algorithm Integration**: Ensures the user performs the required poses and calculates the bloodshot eye percentage.
- **Chatbot**: Created using Google AI Studio and integrated into the app.
- **Firebase**: Utilized for real-time database and crashlytics.

## Repository

Access the project repository on GitHub: [ImpairCheckRepo](https://github.com/mahmoud-elsadany/ImpairCheckRepo)

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/mahmoud-elsadany/ImpairCheckRepo.git
   ```
2. Open the project in Android Studio.
3. Build and run the project on your Android device.

## Usage

1. Launch the app on your device.
2. Follow the on-screen instructions to perform the impairment tests.
3. The app will analyze the data and provide feedback on whether you are fit to work.

## Contributing

1. Fork the repository.
2. Create a new branch (`git checkout -b feature-branch`).
3. Make your changes and commit them (`git commit -m 'Add some feature'`).
4. Push to the branch (`git push origin feature-branch`).
5. Open a pull request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contact

For any inquiries, please contact:
- **Mahmoud Elsadany**
- [Check My Website](http://mahmoudelsadany.space/)

---

**ImpairCheck** - Do the test, save more lives.
