# ==============================================================================
# 第一阶段：构建环境 (Builder Stage)
# 作用：使用包含 JDK 和 Maven 的完整镜像来编译源代码
# ==============================================================================
FROM maven:3.9.11-eclipse-temurin-21 AS builder
# 设置工作目录，后续命令都在这个目录下执行
WORKDIR /workspace

# 1. 优先复制 Maven 构建配置文件
# 技巧：利用 Docker 缓存层机制。
# 只要 pom.xml 等配置文件不变，这一层就会被缓存，
# 从而跳过耗时的依赖下载步骤。
COPY pom.xml ./
COPY app/pom.xml app/

# 2. 预下载依赖 (Dependency Resolution)
# 作用：利用 Docker 缓存层预先下载依赖。只要 Maven 配置不变，这一层就会被缓存。
RUN mvn -pl app -am dependency:go-offline || true

# 3. 复制源代码
# 将整个项目代码复制进来。由于上面的步骤已经缓存了依赖，
# 即使源代码发生变化，也不会触发重新下载依赖。
COPY . .

# 4. 执行构建
# 只构建 app 模块，生成可执行的 JAR 包，跳过单元测试加快构建速度
RUN mvn -pl app -am clean package -DskipTests

# ==============================================================================
# 第二阶段：运行环境 (Runtime Stage)
# 作用：使用轻量级的 JRE 镜像来运行编译好的 JAR 包
# 技巧：多阶段构建 (Multi-stage build) 可以显著减小最终镜像的体积
# ==============================================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# 从第一阶段 (builder) 中复制生成的 JAR 文件到当前镜像
# 只复制构建产物，不复制源代码和 Maven 缓存，保持镜像干净
COPY --from=builder /workspace/app/target/*.jar app.jar

# 声明容器将暴露的端口（仅作为文档说明，实际映射需在 docker-compose 中配置）
EXPOSE 8080

# 容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "app.jar"]
