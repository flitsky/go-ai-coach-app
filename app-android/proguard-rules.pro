# release 빌드에서 R8을 처음 켜면서 추가한 최소 규칙 세트 — Firebase Auth/Credentials/
# Google ID/Play Billing/AdMob은 각 AAR이 consumer-rules.pro를 자체 포함해 AGP가 자동
# 병합하므로(빌드 시 app-android/build/intermediates/aar_metadata 등에서 확인 가능) 여기서
# 라이브러리별 규칙을 중복 작성하지 않는다. 프로젝트 코드에는 리플렉션 기반 직렬화(Gson 등)
# 사용처가 없음을 확인했다(2026-08-12 grep) — 그래서 앱 자체 데이터 클래스용 -keep도 아직
# 없다. 실제 릴리스 빌드로 회귀 테스트 중 리플렉션/콜백 관련 크래시가 발견되면 그 클래스만
# targeted하게 여기에 추가한다 — 앱 패키지 전체를 블랭킷 -keep 하면 이번에 켠 축소/난독화
# 비율이 다시 0%에 가깝게 돌아간다.

# 크래시 리포트의 스택트레이스에 파일명·라인 번호가 남도록 유지한다(클래스/메서드명 자체는
# 여전히 난독화되며, 실제 이름 복원은 Play Console에 업로드하는 mapping.txt가 담당한다).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# androidx.annotation의 @Keep이 실제로 동작하려면 어노테이션 자체가 살아있어야 한다 — 이건
# androidx 라이브러리들의 consumer rules로도 대부분 커버되지만, 없어도 비용이 거의 없어
# 명시적으로 남긴다.
-keepattributes *Annotation*
