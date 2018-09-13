<img src="Graphics/API2AndroidSDKREADMEHeader.png" width="100%" style="width:100%">

A fully comprehensive SDK that gives you access to the VoiceIt's New VoiceIt API 2.0 featuring Voice + Face Verification and Identification right in your Android app.

* [Getting Started](#getting-started)
* [Requirements](#requirements)
* [Installation](#installation)
* [API Calls](#api-calls)
  * [Initialization](#initialization)
  * [Liveness Detection](#liveness-detection)
  * [Encapsulated Methods](#encapsulated-methods)
      * [Encapsulated Voice Enrollment](#encapsulated-voice-enrollment)
      * [Encapsulated Voice Verification](#encapsulated-voice-verification)
      * [Encapsulated Face Enrollment](#encapsulated-face-enrollment)
      * [Encapsulated Face Verification](#encapsulated-face-verification)
      * [Encapsulated Video Enrollment](#encapsulated-video-enrollment)
      * [Encapsulated Video Verification](#encapsulated-video-verification)
  * [User API Calls](#user-api-calls)
      * [Get All Users](#get-all-users)
      * [Create User](#create-user)
      * [Get User](#check-if-user-exists)
      * [Get Groups for User](#get-groups-for-user)
      * [Delete User](#delete-user)
  * [Group API Calls](#group-api-calls)
      * [Get All Groups](#get-all-groups)
      * [Create Group](#create-group)
      * [Get Group](#get-group)
      * [Delete Group](#delete-group)
      * [Group exists](#check-if-group-exists)
      * [Add User to Group](#add-user-to-group)
      * [Remove User from Group](#remove-user-from-group)      
  * [Enrollment API Calls](#enrollment-api-calls)
      * [Get All Voice Enrollments](#get-all-voice-enrollments)
      * [Get All Face Enrollments](#get-all-face-enrollments)
      * [Get All Video Enrollments](#get-all-video-enrollments)
      * [Delete All Enrollments](#delete-all-enrollments)
      * [Delete Voice Enrollment](#delete-voice-enrollment)
      * [Delete Face Enrollment](#delete-face-enrollment)
      * [Delete Video Enrollment](#delete-video-enrollment)
      * [Create Voice Enrollment](#create-voice-enrollment)
      * [Create Voice Enrollment By URL](#create-voice-enrollment-by-url)
      * [Create Video Enrollment](#create-video-enrollment)
      * [Create Video Enrollment By URL](#create-video-enrollment-by-url)
      * [Create Face Enrollment](#create-face-enrollment)
      * [Create Face Enrollment By URL](#create-face-enrollment-by-url)
  * [Verification API Calls](#verification-api-calls)
      * [Voice Verification](#voice-verification)
      * [Voice Verification By URL](#voice-verification-by-url)
      * [Video Verification](#video-verification)
      * [Video Verification By URL](#video-verification-by-url)
      * [Face Verification](#face-verification)
      * [Face Verification By URL](#face-verification-by-url)
  * [Identification API Calls](#identification-api-calls)
      * [Voice Identification](#voice-identification)
      * [Voice Identification By URL](#voice-identification-by-url)
      * [Face Identification](#face-identification)
      * [Face Identification By URL](#face-identification-by-url)
      * [Video Identification](#video-identification)
      * [Video Identification By URL](#video-identification-by-url)

## Getting Started

Sign up for a free Developer Account at <a href="https://voiceit.io/signup" target="_blank">VoiceIt.io</a> and view your API Key and Token in the settings page(as shown below). You can also review the HTTP Documentation at <a href="https://api.voiceit.io" target="_blank">api.voiceit.io</a>.

<img src="Graphics/devSettings.png" alt="API Key and Token" width="400px" />

## Requirements

minSdkVersion: 17

## Installation

VoiceItApi2AndroidSDK is available through [JitPack](https://jitpack.io/#voiceittech/VoiceItApi2AndroidSDK/).

## API Calls

### Initialization

First import *VoiceItAPI2* and then initialize a reference to the SDK inside an Activity, passing in your API Credentials.

```java
import com.loopj.android.http.JsonHttpResponseHandler;
import cz.msebera.android.httpclient.Header;
import org.json.JSONObject;

import com.voiceit.voiceit2.VoiceItAPI2;

public class MainActivity extends AppCompatActivity {

    private VoiceItAPI2 myVoiceIt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myVoiceIt = new VoiceItAPI2("API_KEY","API_TOK");
    }
}
```

### API calls

For each API call, a JsonHttpResponseHandler is needed to receive the result of the call. You can override the response handlers like so, and abbreviated with ellipses below:
```java
new JsonHttpResponseHandler() {
    @Override
    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
        System.out.println("JSONResult : " + response.toString());
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
        if (errorResponse != null) {
            System.out.println("JSONResult : " + errorResponse.toString());
        }
    }
});
```

### Liveness Detection

For our Encapsulated Face and Video Verification methods, liveness detection can be enabled with a passed in boolean parameter and number of checks to make(2 Recommended). 
This enables a a pre-check(prompting to the user to turn their head or smile) before the verification API call to decrease the chance the user is able to spoof with a photo of someone else.

### Encapsulated Methods

Methods that take care of all the logic of enrollment and verification, and the UI in new Android Activities.
Immediately upon calling a method it displays a enrollment/verification view controller that enrolls/verifies the user and 
provides relevant callbacks for whether the API calls were successful or not with associated biometric confidence.
Note: If less than the required enrollments exist for a user, enrollment methods delete them and re-enroll.

#### Encapsulated Voice Enrollment

Create three voice enrollments user with given userId(begins with 'usr_'), contentLanguage('en-US','es-ES' etc.), and a given phrase such as "Never forget tomorrow is a new day".

```java
myVoiceIt.encapsulatedVoiceEnrollment(Activity, "USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", new JsonHttpResponseHandler() {...});
```

#### Encapsulated Voice Verification

Verify user with given userId(begins with 'usr_'), contentLanguage('en-US','es-ES' etc.), and a given phrase such as "Never forget tomorrow is a new day".

```java
myVoiceIt.encapsulatedVoiceVerification(Activity, "USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", new JsonHttpResponseHandler() {...});
```

#### Encapsulated Voice Identification

Identify user from given groupId(begins with 'grp_'), contentLanguage('en-US','es-ES' etc.), and a given phrase such as "Never forget tomorrow is a new day".

```java
myVoiceIt.encapsulatedVoiceIdentification(Activity, "GROUP_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", new JsonHttpResponseHandler() {...});
```

#### Encapsulated Face Enrollment

Create three face enrollments for user with given userId(begins with 'usr_').

```java
myVoiceIt.encapsulatedFaceEnrollment(Activity, "USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Encapsulated Face Verification

Verify user with given userId(begins with 'usr_'), an optional boolean to enable liveness detection and number of liveness checks(2 Recommended).

```java
myVoiceIt.encapsulatedFaceVerification(Activity, "USER_ID_HERE", true, 2, new JsonHttpResponseHandler() {...});
```

#### Encapsulated Face Identification

Identify user from given groupId(begins with 'grp_'), an optional boolean to enable liveness detection and number of liveness checks(2 Recommended).

```java
myVoiceIt.encapsulatedFaceIdentification(Activity, "GROUP_ID_HERE", true, 2, new JsonHttpResponseHandler() {...});
```

#### Encapsulated Video Enrollment

Create three video enrollments for user with given userId(begins with 'usr_') and contentLanguage('en-US','es-ES', etc.), and a given phrase such as "my face and voice identify me".

```java
myVoiceIt.encapsulatedVideoEnrollment(Activity, "USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", new JsonHttpResponseHandler() {...});
```

#### Encapsulated Video Verification

Verify user with given userId(begins with 'usr_'), contentLanguage('en-US','es-ES' etc.), a given phrase such as "my face and voice identify me", and an optional boolean to enable liveness detection and number of liveness checks(2 Recommended).

```java
myVoiceIt.encapsulatedVideoVerification(Activity, "USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", true, 2, new JsonHttpResponseHandler() {...});
```

#### Encapsulated Video Identification

Identify user from given groupId(begins with 'grp_'), contentLanguage('en-US','es-ES' etc.), a given phrase such as "my face and voice identify me", and an optional boolean to enable liveness detection and number of liveness checks(2 Recommended).

```java
myVoiceIt.encapsulatedVideoIdentification(Activity, "GROUP_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", true, 2, new JsonHttpResponseHandler() {...});
```

### User API Calls

#### Get All Users

Get all the users associated with the apiKey
```java
myVoiceIt.getAllUsers(new JsonHttpResponseHandler() {...});
```

#### Create User

Create a new user
```java
myVoiceIt.createUser(new JsonHttpResponseHandler() {...});
```

#### Check if User Exists

Check whether a user exists for the given userId(begins with 'usr_')
```java
myVoiceIt.checkUserExists("USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Delete User

Delete user with given userId(begins with 'usr_')
```java
myVoiceIt.deleteUser("USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Get Groups for User

Get a list of groups that the user with given userId(begins with 'usr_') is a part of
```java
myVoiceIt.getGroupsForUser("USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

### Group API Calls

#### Get All Groups

Get all the groups associated with the apiKey
```java
myVoiceIt.getAllGroups(new JsonHttpResponseHandler() {...});
```

#### Get Group

Returns a group for the given groupId(begins with 'grp_')
```java
myVoiceIt.getGroup("GROUP_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Check if Group Exists

Checks if group with given groupId(begins with 'grp_') exists
```java
myVoiceIt.groupExists("GROUP_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Create Group

Create a new group with the given description
```java
myVoiceIt.createGroup("Sample Group Description", new JsonHttpResponseHandler() {...});
```

#### Add User to Group

Adds user with given userId(begins with 'usr_') to group with given groupId(begins with 'grp_')
```java
myVoiceIt.addUserToGroup("GROUP_ID_HERE", "USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Remove User from Group

Removes user with given userId(begins with 'usr_') from group with given groupId(begins with 'grp_')

```java
myVoiceIt.removeUserFromGroup( "GROUP_ID_HERE", "USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Delete Group

Delete group with given groupId(begins with 'grp_'), note: this call does not delete any users, but simply deletes the group and disassociates the users from the group

```java
myVoiceIt.deleteGroup("GROUP_ID_HERE", new JsonHttpResponseHandler() {...});
```

### Enrollment API Calls

#### Get All Voice Enrollments

Gets all voice enrollments for user with given userId(begins with 'usr_')

```java
myVoiceIt.getAllVoiceEnrollments("USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Get All Face Enrollments

Gets all face enrollments for user with given userId(begins with 'usr_')

```java
myVoiceIt.getFaceEnrollments("USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Get All Video Enrollments

Gets all video enrollments for user with given userId(begins with 'usr_')

```java
myVoiceIt.getAllVideoEnrollments("USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Delete All Enrollments

Delete enrollment for user with given userId(begins with 'usr_') and enrollmentId(integer)

```java
myVoiceIt.deleteAllEnrollments( "USER_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Delete Voice Enrollment

Delete voice enrollment for user with given userId(begins with 'usr_') and enrollmentId(integer)

```java
myVoiceIt.deleteVoiceEnrollment( "USER_ID_HERE", "ENROLLMENT_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Delete Face Enrollment

Delete face enrollment for user with given userId(begins with 'usr_') and faceEnrollmentId(integer)

```java
myVoiceIt.deleteFaceEnrollment( "USER_ID_HERE", "FACE_ENROLLMENT_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Delete Video Enrollment

Delete video enrollment for user with given userId(begins with 'usr_') and enrollmentId(integer)

```java
myVoiceIt.deleteVideoEnrollment( "USER_ID_HERE", "ENROLLMENT_ID_HERE", new JsonHttpResponseHandler() {...});
```

#### Create Voice Enrollment

Create audio enrollment for user with given userId(begins with 'usr_') and contentLanguage('en-US','es-ES' etc.). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds. If the recording argument is left out, the method immediately records the user saying their VoicePrint phrase for 5 seconds.

```java
myVoiceIt.createVoiceEnrollment("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", File recording, new JsonHttpResponseHandler() {...});
```

#### Create Voice Enrollment by URL

Create voice enrollment for user with given userId(begins with 'usr_') and contentLanguage('en-US','es-ES', etc.). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.createVoiceEnrollmentByUrl("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", "URL_TO_AUDIO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

#### Create Video Enrollment

Create video enrollment for user with given userId(begins with 'usr_') and contentLanguage('en-US','es-ES' etc.). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.createVideoEnrollment("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", File video, new JsonHttpResponseHandler() {...});
```

#### Create Video Enrollment by URL

Create video enrollment for user with given userId(begins with 'usr_') and contentLanguage('en-US','es-ES' etc.). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.createVideoEnrollmentByUrl("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", "URL_TO_VIDEO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

#### Create Face Enrollment

Create face enrollment for user with given userId(begins with 'usr_') Note: It is recommended that you send a 2.0 second mp4 video

```java
myVoiceIt.createFaceEnrollment("USER_ID_HERE", File video, new JsonHttpResponseHandler() {...});
```

#### Create Face Enrollment by URL

Create face enrollment for user with given userId(begins with 'usr_') Note: It is recommended that you send a 2.0 second mp4 video

```java
myVoiceIt.createFaceEnrollmentByUrl("USER_ID_HERE", "URL_TO_VIDEO_FILE_HERE", new JsonHttpResponseHandler() {...});
```


### Verification API Calls

#### Voice Verification

Verify user with the given userId(begins with 'usr_') and contentLanguage('en-US','es-ES' etc.). Note: Immediately upon calling this method it records the user saying their VoicePrint phrase for 5 seconds.

```java
myVoiceIt.voiceVerification("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", new JsonHttpResponseHandler() {...});
```


#### Voice Verification by URL
Verify user with the given userId(begins with 'usr_') and contentLanguage('en-US','es-ES', etc.). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.voiceVerificationByUrl("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", "URL_TO_AUDIO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

#### Video Verification

Verify user with given userId(begins with 'usr_') and contentLanguage('en-US','es-ES' etc.). Note: Immediately upon calling this method it displays the camera and starts recording a video of the user saying their VoicePrint phrase for 5 seconds, then sends it to be added as an enrollment and returns the result in the callback

```java
myVoiceIt.videoVerification("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", File video, new JsonHttpResponseHandler() {...});
```

#### Video Verification by URL

Verify user with given userId(begins with 'usr_'), contentLanguage('en-US','es-ES', etc.). Note: File recording needs to be no less than 1.2 seconds and no more than 5 seconds


```java
myVoiceIt.videoVerificationByUrl("USER_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", "URL_TO_VIDEO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

#### Face Verification

Verify user's face with given userId(begins with 'usr_'). Note: Provide an about 2.0 seconds long video(mp4 codec is recommended) of the user's face


```java
myVoiceIt.faceVerification("USER_ID_HERE", File video, new JsonHttpResponseHandler() {...});
```

#### Face Verification by URL

Verify user's face with given userId(begins with 'usr_'). Note: Provide an about 2.0 seconds long video(mp4 codec is recommended) of the user's face


```java
myVoiceIt.faceVerificationByUrl("USER_ID_HERE", "URL_TO_VIDEO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

### Identification API Calls

#### Voice Identification

Identify user inside group with the given groupId(begins with 'grp_') and contentLanguage('en-US','es-ES' etc.). Note: Immediately upon calling this method it records the user saying their VoicePrint phrase for 5 seconds.

```java
myVoiceIt.voiceIdentification("GROUP_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", new JsonHttpResponseHandler() {...});
```

#### Voice Identification by URL

Identify user inside group with the given groupId(begins with 'grp_') and contentLanguage('en-US','es-ES', etc.). Note: File recording needs to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.voiceIdentification("GROUP_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", "URL_TO_AUDIO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

#### Face Identification

Identify user inside group with the given groupId(begins with 'grp_'). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.faceIdentification("GROUP_ID_HERE", File video, new JsonHttpResponseHandler() {...});
```

#### Face Identification by URL

Identify user inside group with the given groupId(begins with 'grp_'). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.faceIdentificationByUrl("GROUP_ID_HERE", "URL_TO_VIDEO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

#### Video Identification

Identify user inside group with the given groupId(begins with 'grp_') and contentLanguage('en-US','es-ES' etc.). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.videoIdentification("GROUP_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", File video, new JsonHttpResponseHandler() {...});
```

#### Video Identification by URL

Identify user inside group with the given groupId(begins with 'grp_') and contentLanguage('en-US','es-ES' etc.). Note: File recording need to be no less than 1.2 seconds and no more than 5 seconds

```java
myVoiceIt.videoIdentificationByUrl("GROUP_ID_HERE", "CONTENT_LANGUAGE_HERE", "PHRASE_HERE", "URL_TO_VIDEO_FILE_HERE", new JsonHttpResponseHandler() {...});
```

## Author

Stephen Akers, stephen@voiceit.io

## License

VoiceItApi2AndroidSDK is available under the MIT license. See the LICENSE file for more info.
