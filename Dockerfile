FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# 의존성 다운로드를 소스와 다른 레이어에 둬서 소스만 바뀌면 다시 받지 않게 한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# build.gradle의 bootRun·test와 같은 JVM 타임존을 건다.
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
