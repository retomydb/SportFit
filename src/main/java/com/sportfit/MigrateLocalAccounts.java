package com.sportfit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
@Configuration @ConditionalOnProperty(name="sportfit.migrate-local",havingValue="true")
class MigrateLocalAccounts {
 @Bean ApplicationRunner migrate(JdbcTemplate db,ObjectMapper json,AccountStore remote){return args->{if(!(remote instanceof GitHubAccountStore))throw new IllegalStateException("Migration requires GitHub storage");var local=new LocalAccountStore(db,json);var emails=new HashSet<String>(db.queryForList("SELECT email FROM users",String.class));emails.addAll(db.queryForList("SELECT email FROM account_documents",String.class));int copied=0,existing=0;for(String email:emails){if(remote.create(email,local.read(email)))copied++;else existing++;}System.out.println("Migration finished: "+copied+" encrypted accounts copied; "+existing+" existing remote accounts preserved.");};}
}
