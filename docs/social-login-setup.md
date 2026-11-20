# 소셜 로그인(Google · Kakao) 로컬 설정

값은 Git에 올리지 않는 `local.properties`(프로젝트 루트) 또는 환경 변수로 주입한다.
값이 없어도 빌드·이메일 로그인은 동작하며, 소셜 버튼은 "설정되지 않은 빌드예요"를 표시한다.

```properties
# local.properties
dq.googleServerClientId=<Web OAuth Client ID>
dq.kakaoNativeAppKey=<Kakao Native App Key>
```

환경 변수: `DQ_GOOGLE_SERVER_CLIENT_ID`, `DQ_KAKAO_NATIVE_APP_KEY`

값은 백엔드 팀에서 전달받는다. 변경 후에는 다시 빌드해야 한다(BuildConfig · Manifest 반영).

## 디버그 서명 키 확인

등록 값과 내 PC의 키가 같은지 확인한다. (JDK가 없으면 Android Studio 내장 `jbr/Contents/Home/bin/keytool` 사용)

```bash
# Google: SHA-1 (Firebase/Google Cloud에 등록된 값과 비교)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1

# Kakao: 키 해시 (Kakao Developers > 플랫폼 > Android에 등록된 값과 비교)
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | openssl sha1 -binary | openssl base64
```

## 참고

- Google: Credential Manager + `GetSignInWithGoogleOption`, 앱은 테스트 모드(등록된 테스트 사용자만 로그인 가능).
- Kakao: 개발 상태(팀 멤버만 로그인 가능), OpenID Connect 활성화 필수. 백엔드에는 Access Token이 아닌 ID Token을 보낸다.
- 에뮬레이터에 카카오톡이 없으면 카카오계정(브라우저) 로그인이 사용된다.

## 에뮬레이터 문제 해결

### Kakao 로그인(브라우저) 시 검은 화면

카카오계정 로그인 화면이 검은 화면으로 표시되는 경우, 에뮬레이터를 소프트웨어 렌더링으로 실행하면 해결된 사례가 있다. 앱 코드 변경 없이 해결됐다.

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_8 -gpu swiftshader -feature -Vulkan -no-snapshot-load
```

> 정확한 그래픽 오류 원인은 확정되지 않았다. 에뮬레이터 GPU/Vulkan 설정과 WebView 렌더링 문제로 추정될 뿐 검증하지 못했다.
> 위 방법은 개발용 에뮬레이터 우회책이며, 소프트웨어 렌더링이라 다소 느릴 수 있다.
