<p align="center">
  <img src="assets/dextop-readme-icon.png" alt="Dextop" width="192">
</p>

<h1 align="center">Dextop</h1>

<p align="center">
  <a href="README.md">English</a> | <a href="README.ja.md">日本語</a> | <a href="README.zh-CN.md">简体中文</a> | <a href="README.ko.md">한국어</a>
</p>

Dextop은 Android 기기에 가상 디스플레이를 만들고 스마트폰만으로 데스크톱과 같은 작업 환경을 제공하는 오픈 소스 앱입니다. Stellar 또는 Shizuku와 Android 시스템 서비스를 사용하여 앱 실행, 창 배치, 터치 입력, 화면 방향 및 관련 데스크톱 동작을 제어합니다.

## 커뮤니티 및 피드백

공식 Discord 서버에 참여하세요: [참여하기](https://discord.com/invite/444YG3srK)

버그 신고, 기기 동작 보고 및 기능 요청을 제출할 수 있습니다.

## 스크린샷 및 데모

<table>
  <tr>
    <td width="20%" align="center"><img src="docs/media/home.jpg" alt="Dextop 홈 화면"><br><sub>홈 및 작업 공간</sub></td>
    <td width="20%" align="center"><img src="docs/media/desktop.jpg" alt="Dextop 데스크톱"><br><sub>데스크톱</sub></td>
    <td width="20%" align="center"><img src="docs/media/control-overlay.jpg" alt="Dextop 제어 오버레이"><br><sub>제어 오버레이</sub></td>
    <td width="20%" align="center"><img src="docs/media/multi-window.jpg" alt="Dextop 멀티 윈도우 작업 공간"><br><sub>멀티 윈도우 작업 공간</sub></td>
    <td width="20%" align="center"><a href="docs/media/dextop-demo.mp4"><img src="docs/media/demo-poster.jpg" alt="Dextop 데모 동영상 재생"></a><br><sub>▶ 데모 동영상</sub></td>
  </tr>
</table>

## 기능

- [x] 해상도, 밀도 및 가로·세로 방향을 설정할 수 있는 가상 디스플레이
- [x] 보안 디스플레이 및 Android 시스템 장식 제어
- [x] 데스크톱 앱 런처
- [x] 여러 앱의 배치를 저장하고 복원하는 작업 공간
- [x] 2분할, 3분할, 4분할 및 기타 창 레이아웃
- [x] JSON 형식의 작업 공간 가져오기 및 내보내기
- [x] 커서 및 직접 터치 입력 모드
- [x] 탭, 길게 누르기, 드래그, 오른쪽 클릭, 두 손가락 및 세 손가락 제스처
- [x] 스크롤과 핀치 줌을 포함한 멀티 터치 입력
- [x] 물리 마우스 지원
- [x] 물리 키보드 지원
- [x] 지원 기기에서 Dextop과 외부 디스플레이 간 마우스·키보드 입력 라우팅
- [x] 모니터 배치 저장과 디스플레이 간 포인터 이동을 지원하는 멀티 디스플레이 토폴로지
- [x] 데스크톱 작업 표시줄 자동 숨김 및 선택적 내장 디스플레이 120Hz 강제 적용
- [x] US 키보드, 트랙패드, 수동 오버레이 제어 및 선택적 힌지 각도 감지를 제공하는 폴더블 노트북 모드
- [x] 폴더블 메인/커버 디스플레이 전환
- [x] 헤드 유닛 크기 자동 조정, Dextop/스마트폰 소스 선택 및 터치 전달을 지원하는 주차 중 Android Auto 미러 활동
- [x] FPS, 화면 주사율, 메모리, 배터리 및 예상 소비 전력을 표시하는 성능 오버레이
- [x] 빠른 설정 타일 실행
- [x] 중단된 세션 복구 및 임시 Android 설정 복원
- [x] 앱 로그, 기능 감지 결과, 폴백 결과 및 기기 사양을 포함하는 상세 진단 보고서
- [x] 기능별 결과와 자동 이메일 작성을 지원하는 현지화된 기기 호환성 보고서
- [x] 일본어, 영어, 중국어, 한국어 및 러시아어 인터페이스

## 호환성

| 환경 | 상태 | 참고 사항 |
| --- | --- | --- |
| Samsung DeX(One UI 8 이상) | 완전 지원 | 현재 가장 완성도가 높은 환경입니다. DeX가 관리하는 기능에는 Samsung 플랫폼 구현이 사용됩니다. |
| Samsung DeX(One UI 8 미만) | 제한적이며 호환되지 않을 가능성이 높음 | 이전 DeX 구현은 Dextop에 필요한 디스플레이 및 창 관리 동작을 제공하지 않을 수 있습니다. |
| Google Pixel | 제한적이며 미완성 | Android의 자유 형식/데스크톱 구현과 숨겨진 API 사용 가능 여부에 따라 달라집니다. 일부 기능이 작동하지 않을 수 있습니다. |
| OPPO ColorOS 데스크톱 | 제한적이며 미완성 | 데스크톱은 표시할 수 있지만 작업 표시줄과 같은 플랫폼 구성 요소가 나타나지 않을 수 있습니다. |
| HyperOS 이상을 실행하는 Xiaomi 기기 | 비활성화 | MIUI와 HyperOS는 지원하지 않습니다. |
| 기타 Android 기기 | 실험적 | 가상 디스플레이, 미러링 및 자유 형식 창 지원은 제조사, 모델 및 OS 업데이트에 따라 다릅니다. |

Android Auto 항목은 Android 15 이상에서 주차 앱용 `CAR_LAUNCHER` 활동으로 제공됩니다. 사이드로드 앱을 표시할지는 Android Auto 호스트가 결정하며, 현재 공개된 주차 앱 지원은 승인된 카테고리로 제한됩니다.

Dextop은 런타임에 기기 기능을 확인하고 호환 가능한 백엔드를 순서대로 시도합니다. Android 숨겨진 API와 OEM 동작에 의존하므로 동일 제조사의 기기라도 모델과 OS 버전에 따라 결과가 다를 수 있습니다.

<details>
<summary><strong>지원 기기</strong></summary>

아래 상태는 실제로 테스트한 펌웨어 버전에만 적용됩니다. 제조사를 펼치면 해당 기기를 확인할 수 있습니다. 기능별 결과는 [기기 호환성 위키](https://github.com/NarYuki/Dextop/wiki/Device-Compatibility)를 참조하세요.

<details>
<summary><strong>Samsung</strong></summary>

| 기기 | 모델 | 테스트한 소프트웨어 | 상태 |
| --- | --- | --- | --- |
| Galaxy S26 | SM-S942Z (`m1q`) | Android 16 / One UI 8.5 / `S942ZSCS1AZF2` | ✅ 작동 확인 |
| Galaxy Z TriFold | SM-F968N (`q7mq`) | Android 16(API 36) / One UI 8.0 / `F968NKSS6BZG3` | ✅ 작동 확인 |
| Galaxy Z Fold8 | SM-F971Q (`h8q`) | Android 17(API 37) / One UI 9.0 / `F971QOPU1AZGI` | ✅ 작동 확인 |
| Galaxy Z Fold7 | SM-F966Q (`q7q`) | Android 16(API 36) / One UI 8.0 / `F966QOPU1BZF1` | ✅ 작동 확인 |
| Galaxy Z Fold3 5G | SCG11 (`SCG11`) | Android 15(API 35) / One UI 7.0 / `SCG11KDS1EZB8` | ❌ 현재 작동하지 않음 |

_커뮤니티에서 제출하고 검토한 기기 보고서_

</details>

<details>
<summary><strong>Google</strong></summary>

| 기기 | 모델 | 테스트한 소프트웨어 | 상태 |
| --- | --- | --- | --- |
| Pixel 9a | Pixel 9a (`tegu`) | Android 17(API 37) / `15641320` | 🟡 부분 지원 |

_커뮤니티에서 제출하고 검토한 기기 보고서_

</details>

<details>
<summary><strong>HONOR</strong></summary>

| 기기 | 모델 | 테스트한 소프트웨어 | 상태 |
| --- | --- | --- | --- |
| HONOR Magic 8 Pro | BKQ-AN10 (`HNBKQ`) | Android 16(API 36) / `10DLDLD170SP5C00E167` | 🧪 실험적 |

_커뮤니티에서 제출하고 검토한 기기 보고서_

</details>

<details>
<summary><strong>OPPO</strong></summary>

> ColorOS에서는 데스크톱을 표시할 수 있지만 지원이 완전하지 않으며 작업 표시줄이 나타나지 않을 수 있습니다.

| 기기 | 모델 | 테스트한 소프트웨어 | 상태 |
| --- | --- | --- | --- |
| Find X9 | OPG07 (`OP5E8BL1`) | Android 16(API 36) / ColorOS 16 / `B.R4T3.1287153_118ce71_119cc78` | 🧪 실험적 |

_커뮤니티에서 제출하고 검토한 기기 보고서_

</details>

<details>
<summary><strong>Sony</strong></summary>

| 기기 | 모델 | 테스트한 소프트웨어 | 상태 |
| --- | --- | --- | --- |
| Xperia 1 III | XQ-BC42 (`XQ-BC42`) | Android 13(API 33) / `061002A0000472A1434898470` | ❌ 현재 작동하지 않음 |

_커뮤니티에서 제출하고 검토한 기기 보고서_

</details>

<details>
<summary><strong>Xiaomi</strong></summary>

> HyperOS 이상을 실행하는 Xiaomi 기기에서는 데스크톱 환경이 비활성화됩니다. MIUI와 HyperOS는 지원하지 않습니다.

| 기기 | 모델 | 테스트한 소프트웨어 | 상태 |
| --- | --- | --- | --- |
| POCO X7 Pro 5G | 2412DPC0AG (`rodin`) | Android 16(API 36) / HyperOS 3.0 / `OS3.0.301.0.WOJMIXM` | ❌ 현재 작동하지 않음 |
| POCO X7 Pro | 2412DPC0AG (`rodin`) | Android 16(API 36) / HyperOS 3.0 / `OS3.0.301.0.WOJMIXM` | ❌ 현재 작동하지 않음 |

_커뮤니티에서 제출하고 검토한 기기 보고서_

</details>

</details>

## 시스템 요구 사항

- Android 10 이상. 대부분의 기기에서 실용적인 데스크톱 환경을 사용하려면 Android 14 이상이 필요합니다.
- [Stellar](https://github.com/roro2239/Stellar/releases)(기본 및 권장, 특히 Android 16 이상) 또는 [Shizuku](https://github.com/RikkaApps/Shizuku/releases)
- 무선 디버깅, ADB 또는 루트로 선택한 서비스 실행
- Dextop에 Stellar 또는 Shizuku 권한 부여

Dextop의 **GitHub에서 다운로드** 버튼으로 Stellar를 다운로드하세요. Android 16 이상에서는 Stellar 사용을 권장합니다. Android 16 이상에서 Shizuku를 사용하는 경우 Play 스토어 버전이 아닌 [GitHub Releases 빌드](https://github.com/RikkaApps/Shizuku/releases)를 설치하세요. Android 15 이하에서는 Play 스토어 버전도 사용할 수 있습니다. 두 관리자가 모두 설치되어 있으면 Dextop에서 사용할 관리자를 선택하도록 요청하며, 어느 한쪽을 제거할 때까지 선택 사항을 유지합니다.

무선 디버깅 설정 과정이 불분명하다면 [Shizuku 공식 설정 안내서](https://shizuku.rikka.app/guide/setup/)의 **무선 디버깅으로 시작**을 따르세요. Stellar를 무선으로 시작할 때도 동일한 Android 페어링 절차를 사용합니다.

## 설치

Google Play 출시는 현재 검토 중입니다.

[GitHub Releases](https://github.com/NarYuki/Dextop/releases/latest)에서 최신 APK를 다운로드하여 설치하세요.

### Nightly 빌드

[GitHub Actions](https://github.com/NarYuki/Dextop/actions)에서 최신 개발 빌드를 받을 수 있습니다. 아직 안정 버전에 포함되지 않은 변경 사항을 사용하려면 가장 최근에 성공한 **Debug APK** 워크플로 실행을 열고 Nightly 아티팩트를 다운로드하세요. 아티팩트에는 서로 일치하는 Dextop 및 Dextop Car Companion 디버그 APK가 포함됩니다. Nightly 빌드는 최신 소스에서 생성되는 베타 빌드이므로 미완성 기능이나 회귀 문제가 포함될 수 있습니다.

Android Auto 지원이 포함된 안정적인 GitHub Release에는 Dextop APK와 이에 맞는 **Dextop Car Companion** APK가 함께 제공됩니다. 서명과 릴레이 프로토콜이 일치하도록 두 APK를 동일한 릴리스에서 설치하세요.

## Android Auto 빠른 시작

Dextop은 Android 15 이상에서 **Dextop Car Companion**을 통해 지원되는 주차 상태의 Android Auto 디스플레이에 전용 데스크톱을 제공합니다.

1. 동일한 릴리스에서 Dextop과 이에 맞는 **Dextop Car Companion** APK를 설치합니다.
2. Stellar 또는 Shizuku를 시작하고 Dextop에 권한을 부여한 뒤 스마트폰에서 Dextop 설정을 완료합니다.
3. 주차 중 Android Auto를 연결하고 차량 런처에서 **Dextop Car Companion**을 엽니다.
4. **시작**을 선택합니다. 터치 입력은 헤드 유닛에서 Android Auto 전용 Dextop 디스플레이로 직접 전달됩니다.
5. 차량 디스플레이의 왼쪽 가장자리에서 오른쪽으로 스와이프하면 작업 공간, 영상 재연결 및 세션 종료를 위한 Android Auto 제어 메뉴가 열립니다.

특정 헤드 유닛에서 사이드로드된 주차 앱을 표시할지는 Android Auto가 결정합니다. 기본 호환 모드에서는 Android Auto 가상 디스플레이 오버레이가 스마트폰에 표시될 수 있으며, **Dextop → 설정 → Auto**에서 실험적인 숨김 디스플레이 모드를 사용할 수 있습니다. 설치 세부 정보, 디스플레이 모드, 제스처, 제어 기능, 제한 사항, DHU 테스트 및 문제 해결은 [Android Auto 위키](https://github.com/NarYuki/Dextop/wiki/Android-Auto)를 참조하세요.

## 개발

```sh
git clone https://github.com/NarYuki/Dextop.git
cd Dextop
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

다른 기기 지원에 기여하려면 [기기 지원 추가](docs/ADDING_DEVICE_SUPPORT.en.md)를 읽어보세요. 일본어 안내서는 [여기](docs/ADDING_DEVICE_SUPPORT.md)에서 확인할 수 있습니다.

## 진단

**설정 → 앱 정보 → 동작 로그 및 기기 진단**을 열어 기기 사양, 기능 감지 결과, 폴백 결과 및 Dextop 동작 로그를 확인하거나 복사하고 공유할 수 있습니다. 이슈에 보고서를 첨부하기 전에 공개하고 싶지 않은 개인 정보를 삭제하세요.

## 기기 보고서

**설정 → 기기 보고서**에서 특정 기기와 펌웨어에서 Dextop의 동작을 보고할 수 있습니다. 전체 결과와 나열된 각 기능에 대해 **작동**, **작동하지 않음** 또는 **테스트하지 않음**을 선택하고 필요한 경우 메모를 추가한 뒤 **이메일로 보고서 보내기**를 누르세요. Dextop이 구조화된 Markdown 보고서를 작성하고 수신자가 `dextop-device@n4t.su`로 지정된 이메일 앱을 엽니다.

보고서에는 기기 모델, 코드명, Android/API 버전, 펌웨어 식별자, 보안 패치, Dextop 버전, 감지된 기능 및 선택한 결과가 포함됩니다. 보내기 전에 생성된 이메일을 검토하세요. 전체 필드 목록과 절차는 [기기 보고서](https://github.com/NarYuki/Dextop/wiki/Device-Reports)를 참조하세요.

이 프로젝트는 활발히 개발되고 있습니다. 사용 가능한 기능과 동작은 기기 펌웨어 및 Android 업데이트에 따라 변경될 수 있습니다.

## 라이선스

GPL-3.0-or-later에 따라 라이선스가 부여됩니다. 자세한 내용은 [LICENSE](LICENSE)를 참조하세요.
