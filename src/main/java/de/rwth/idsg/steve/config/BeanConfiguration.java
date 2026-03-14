/*
 * SteVe - SteckdosenVerwaltung - https://github.com/steve-community/steve
 * Copyright (C) 2013-2026 SteVe Community Team
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mysql.cj.conf.PropertyKey;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.rwth.idsg.steve.SteveConfiguration;
import de.rwth.idsg.steve.service.DummyReleaseCheckService;
import de.rwth.idsg.steve.service.GithubReleaseCheckService;
import de.rwth.idsg.steve.service.ReleaseCheckService;
import de.rwth.idsg.steve.utils.DateTimeUtils;
import de.rwth.idsg.steve.utils.InternetChecker;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import javax.annotation.PreDestroy;
import javax.validation.Validator;
import java.util.List;
import java.util.concurrent.*;

import static de.rwth.idsg.steve.SteveConfiguration.CONFIG;

@Slf4j
@Configuration
@EnableWebMvc
@EnableScheduling
@ComponentScan("de.rwth.idsg.steve")
public class BeanConfiguration implements WebMvcConfigurer {

    private HikariDataSource mainDataSource;
    private HikariDataSource secondaryDataSource;
    private HikariDataSource phpDataSource;
    private ScheduledThreadPoolExecutor executor;

    // -------------------------------------------------------
    // Main DataSource
    // -------------------------------------------------------

    private void initDataSource() {

        if (mainDataSource != null) return;

        SteveConfiguration.DB db = CONFIG.getDb();

        HikariConfig hc = new HikariConfig();

        hc.setJdbcUrl("jdbc:mysql://" + db.getIp() + ":" + db.getPort() + "/" + db.getSchema());
        hc.setUsername(db.getUserName());
        hc.setPassword(db.getPassword());

        hc.setMaximumPoolSize(8);
        hc.setMinimumIdle(2);
        hc.setIdleTimeout(300000);
        hc.setMaxLifetime(1200000);
        hc.setConnectionTimeout(20000);
        hc.setLeakDetectionThreshold(60000);

        mysqlPerformance(hc);

        mainDataSource = new HikariDataSource(hc);
    }

    // -------------------------------------------------------
    // Secondary DataSource
    // -------------------------------------------------------

    private void initSecondaryDataSource() {

        if (secondaryDataSource != null) return;

        SteveConfiguration.DB db = CONFIG.getDb2();

        HikariConfig hc = new HikariConfig();

        hc.setJdbcUrl("jdbc:mysql://" + db.getIp() + ":" + db.getPort() + "/" + db.getSchema());
        hc.setUsername(db.getUserName());
        hc.setPassword(db.getPassword());

        hc.setMaximumPoolSize(4);
        hc.setMinimumIdle(1);

        mysqlPerformance(hc);

        secondaryDataSource = new HikariDataSource(hc);
    }

    // -------------------------------------------------------
    // PHP DataSource
    // -------------------------------------------------------

    private void initPhpDataSource() {

        if (phpDataSource != null) return;

        SteveConfiguration.DB db = CONFIG.getDb3();

        HikariConfig hc = new HikariConfig();

        hc.setJdbcUrl("jdbc:mysql://" + db.getIp() + ":" + db.getPort() + "/" + db.getSchema());
        hc.setUsername(db.getUserName());
        hc.setPassword(db.getPassword());

        hc.setMaximumPoolSize(2);
        hc.setMinimumIdle(1);

        mysqlPerformance(hc);

        phpDataSource = new HikariDataSource(hc);
    }

    // -------------------------------------------------------
    // MySQL Optimization
    // -------------------------------------------------------

    private void mysqlPerformance(HikariConfig hc) {

        hc.addDataSourceProperty(PropertyKey.cachePrepStmts.getKeyName(), true);
        hc.addDataSourceProperty(PropertyKey.useServerPrepStmts.getKeyName(), true);
        hc.addDataSourceProperty(PropertyKey.prepStmtCacheSize.getKeyName(), 250);
        hc.addDataSourceProperty(PropertyKey.prepStmtCacheSqlLimit.getKeyName(), 2048);

        hc.addDataSourceProperty(PropertyKey.characterEncoding.getKeyName(), "utf8");
        hc.addDataSourceProperty(PropertyKey.connectionTimeZone.getKeyName(), CONFIG.getTimeZoneId());
        hc.addDataSourceProperty(PropertyKey.useSSL.getKeyName(), false);

        hc.addDataSourceProperty("cacheResultSetMetadata", true);
        hc.addDataSourceProperty("maintainTimeStats", false);
        hc.addDataSourceProperty("useLocalSessionState", true);
        hc.addDataSourceProperty("elideSetAutoCommits", true);
        hc.addDataSourceProperty("tcpKeepAlive", true);
        hc.addDataSourceProperty("rewriteBatchedStatements", true);
    }

    // -------------------------------------------------------
    // DSL Contexts
    // -------------------------------------------------------

    @Primary
    @Bean
    public DSLContext dslContext() {

        initDataSource();

        Settings settings = new Settings()
                .withAttachRecords(false)
                .withExecuteLogging(CONFIG.getDb().isSqlLogging());

        org.jooq.Configuration conf = new DefaultConfiguration()
                .set(SQLDialect.MYSQL)
                .set(new DataSourceConnectionProvider(mainDataSource))
                .set(settings);

        return DSL.using(conf);
    }

    @Bean
    @Qualifier("secondary")
    public DSLContext secondaryDslContext() {

        initSecondaryDataSource();

        Settings settings = new Settings()
                .withAttachRecords(false)
                .withExecuteLogging(CONFIG.getDb2().isSqlLogging());

        org.jooq.Configuration conf = new DefaultConfiguration()
                .set(SQLDialect.MYSQL)
                .set(new DataSourceConnectionProvider(secondaryDataSource))
                .set(settings);

        return DSL.using(conf);
    }

    @Bean
    @Qualifier("php")
    public DSLContext phpDslContext() {

        initPhpDataSource();

        Settings settings = new Settings()
                .withAttachRecords(false)
                .withExecuteLogging(CONFIG.getDb3().isSqlLogging());

        org.jooq.Configuration conf = new DefaultConfiguration()
                .set(SQLDialect.MYSQL)
                .set(new DataSourceConnectionProvider(phpDataSource))
                .set(settings);

        return DSL.using(conf);
    }

    // -------------------------------------------------------
    // Scheduler
    // -------------------------------------------------------

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("SteVe-Executor-%d")
                .build();

        executor = new ScheduledThreadPoolExecutor(20, threadFactory);
        executor.setRemoveOnCancelPolicy(true);

        return executor;
    }

    // -------------------------------------------------------
    // Validator
    // -------------------------------------------------------

    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }

    // -------------------------------------------------------
    // Release Check
    // -------------------------------------------------------

    @Bean
    public ReleaseCheckService releaseCheckService() {

        if (InternetChecker.isInternetAvailable()) {
            return new GithubReleaseCheckService();
        } else {
            return new DummyReleaseCheckService();
        }
    }

    @EventListener
    public void afterStart(ContextRefreshedEvent event) {
        DateTimeUtils.checkJavaAndMySQLOffsets(dslContext());
    }

    // -------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------

    @PreDestroy
    public void shutDown() {

        if (mainDataSource != null) mainDataSource.close();
        if (secondaryDataSource != null) secondaryDataSource.close();
        if (phpDataSource != null) phpDataSource.close();

        if (executor != null) gracefulShutDown(executor);
    }

    private void gracefulShutDown(ExecutorService executor) {

        try {

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

        } catch (InterruptedException e) {

            log.error("Termination interrupted", e);

        } finally {

            if (!executor.isTerminated()) {
                log.warn("Killing non-finished tasks");
            }

            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------
    // Web Config
    // -------------------------------------------------------

    @Bean
    public InternalResourceViewResolver urlBasedViewResolver() {

        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/WEB-INF/views/");
        resolver.setSuffix(".jsp");

        return resolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {

        registry.addViewController("/manager/signin")
                .setViewName("signin");

        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    // -------------------------------------------------------
    // JSON Config
    // -------------------------------------------------------

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {

        for (HttpMessageConverter<?> converter : converters) {

            if (converter instanceof MappingJackson2HttpMessageConverter) {

                MappingJackson2HttpMessageConverter conv = (MappingJackson2HttpMessageConverter) converter;

                ObjectMapper objectMapper = conv.getObjectMapper();

                objectMapper.configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                objectMapper.configure(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

                break;
            }
        }
    }

    @Bean
    public ObjectMapper objectMapper(RequestMappingHandlerAdapter adapter) {

        return adapter.getMessageConverters()
                .stream()
                .filter(c -> c instanceof MappingJackson2HttpMessageConverter)
                .findAny()
                .map(c -> ((MappingJackson2HttpMessageConverter) c).getObjectMapper())
                .orElseThrow(() ->
                        new RuntimeException("No MappingJackson2HttpMessageConverter found"));
    }
}