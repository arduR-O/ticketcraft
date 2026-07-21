package com.ticketcraft.gateway.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Configuration class to manually initialize Liquibase database migrations.
 * 
 * Why: Since this is a reactive API Gateway using WebFlux and R2DBC, Spring Boot's
 * default JDBC DataSourceAutoConfiguration is not automatically triggered. However,
 * Liquibase requires a standard blocking JDBC connection to apply schema migrations.
 * Therefore, we explicitly configure a JDBC DataSource and SpringLiquibase bean to
 * create the 'users' table in the gateway database during application startup.
 */
@Configuration
public class LiquibaseConfig {

  @Value("${spring.datasource.url}")
  private String url;

  @Value("${spring.datasource.username}")
  private String username;

  @Value("${spring.datasource.password}")
  private String password;

  @Value("${spring.datasource.driver-class-name}")
  private String driverClassName;

  @Value("${spring.liquibase.change-log}")
  private String changeLog;

  /**
   * Creates a standard JDBC DataSource.
   * 
   * Why: Liquibase migrations run synchronously over JDBC and cannot use the reactive
   * R2DBC connection pool. We construct this DataSource using standard JDBC configuration
   * parameters to give Liquibase a synchronous database connection.
   *
   * @return the configured DataSource instance
   */
  @Bean
  public DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(driverClassName);
    dataSource.setUrl(url);
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    return dataSource;
  }

  /**
   * Configures and starts the Liquibase migration processor.
   * 
   * Why: This bean intercepts the startup lifecycle to run the configured changelog
   * (e.g., creating the users table) before the application starts accepting web traffic.
   *
   * @param dataSource the JDBC DataSource to execute migrations on
   * @return the SpringLiquibase service bean
   */
  @Bean
  public SpringLiquibase liquibase(DataSource dataSource) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setChangeLog(changeLog);
    liquibase.setDataSource(dataSource);
    return liquibase;
  }
}
