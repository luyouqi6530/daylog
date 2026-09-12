# ---------- 阶段一：Maven 构建 ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

# 配置阿里云镜像加速依赖下载（国内网络环境）
RUN mkdir -p /root/.m2 && \
    printf '%s\n' \
    '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
    '  <mirrors>' \
    '    <mirror>' \
    '      <id>aliyunmaven</id>' \
    '      <mirrorOf>central</mirrorOf>' \
    '      <name>Aliyun Maven</name>' \
    '      <url>https://maven.aliyun.com/repository/public</url>' \
    '    </mirror>' \
    '  </mirrors>' \
    '</settings>' > /root/.m2/settings.xml

# 先拷贝 pom 并预下载依赖，利用 Docker 分层缓存加速重复构建
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# 拷贝源码并打包（-DskipTests 跳过测试：测试类依赖外部 MySQL/Redis）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 阶段二：JRE 运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 上传目录（由 docker-compose 以命名卷挂载持久化）
RUN mkdir -p /app/uploads

COPY --from=build /build/target/daylog-backend-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

# -XX:MaxRAMPercentage 让 JVM 自动适配容器内存限制（配合 compose 的 mem_limit）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
