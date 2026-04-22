package interview.guide.infrastructure.file;

import interview.guide.common.config.StorageConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 应用启动时确保配置的 S3 桶存在，避免 RustFS 等环境需手工建桶。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage", name = "auto-create-bucket", havingValue = "true",
    matchIfMissing = true)
public class StorageBucketBootstrap implements ApplicationRunner {

    private final FileStorageService fileStorageService;
    private final StorageConfigProperties storageConfig;

    @Override
    public void run(ApplicationArguments args) {
        log.info("初始化对象存储桶: {}", storageConfig.getBucket());
        fileStorageService.ensureBucketExists();
    }
}
