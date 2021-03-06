package com.github.xingren.datasource.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IKeyGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.optimize.JsqlParserCountOptimize;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.github.xingren.datasource.bean.DynamicDataSource;
import org.apache.ibatis.plugin.Interceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author HuZhenSha
 * @date 2021/4/29 10:48
 */
@MapperScan("com.github.xingren.datasource.mapper")
@ComponentScan("com.github.xingren.datasource")
public class MyBatisPlusConfig {

    private final ApplicationContext applicationContext;

    public MyBatisPlusConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean("master")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource master() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean("dynamicDataSource")
    public DynamicDataSource dynamicDataSource(@Qualifier("master") DataSource dataSource) {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("master", dataSource);
        // ??? master ???????????????????????????????????????
        //dynamicDataSource.setDefaultTargetDataSource(dataSource);
        // ??? master ??? slave ?????????????????????????????????
        dynamicDataSource.setTargetDataSources(dataSourceMap);
        return dynamicDataSource;
    }

    @Bean
    public MybatisSqlSessionFactoryBean sqlSessionFactoryBean(@Qualifier("dynamicDataSource") DynamicDataSource dataSource,
                                                              @Qualifier("paginationInterceptor") PaginationInterceptor paginationInterceptor) throws Exception {

        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        // ??????????????????????????????
        Interceptor[] plugins = new Interceptor[1];
        plugins[0] = paginationInterceptor;
        sessionFactory.setPlugins(plugins);
        //??????????????????????????????????????????????????????????????? dynamicDataSource????????????????????????????????????
        sessionFactory.setDataSource(dataSource);
        // ??????Model
        //sessionFactory.setTypeAliasesPackage("com.naic.com.github.xingren.datasource.mapper.*");
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // ??????????????????
        sessionFactory.setMapperLocations(resolver.getResources("classpath*:mapper/**/*Mapper.xml"));

        // ??????????????????
        GlobalConfig globalConfig = new GlobalConfig();
        // ???????????????
        this.getBeanThen(MetaObjectHandler.class, globalConfig::setMetaObjectHandler);
        // ?????????????????????
        this.getBeanThen(IKeyGenerator.class, i -> globalConfig.getDbConfig().setKeyGenerator(i));
        // ??????sql?????????
        this.getBeanThen(ISqlInjector.class, globalConfig::setSqlInjector);
        // ??????ID?????????
        this.getBeanThen(IdentifierGenerator.class, globalConfig::setIdentifierGenerator);
        sessionFactory.setGlobalConfig(globalConfig);

        return sessionFactory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(@Qualifier("dynamicDataSource") DataSource dataSource) {
        // ??????????????????, ????????????????????????????????????@Transactional????????????
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * ????????????
     *
     * @return bean
     */
    @Bean
    public PaginationInterceptor paginationInterceptor() {
        PaginationInterceptor paginationInterceptor = new PaginationInterceptor();
        // ?????? count ???join??????????????????left join
        paginationInterceptor.setCountSqlParser(new JsqlParserCountOptimize(true));
        return paginationInterceptor;
    }

    /**
     * ??????spring???????????????????????????bean,??????????????????
     *
     * @param clazz    class
     * @param consumer ??????
     * @param <T>      ??????
     */
    private <T> void getBeanThen(Class<T> clazz, Consumer<T> consumer) {
        if (this.applicationContext.getBeanNamesForType(clazz, false, false).length > 0) {
            consumer.accept(this.applicationContext.getBean(clazz));
        }
    }

}
