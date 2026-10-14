package com.sportfit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.function.UnaryOperator;
@Repository @ConditionalOnProperty(name="sportfit.storage",havingValue="h2",matchIfMissing=true)
public class LocalAccountStore implements AccountStore {
 private final JdbcTemplate db;private final ObjectMapper json;
 public LocalAccountStore(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 private Map<String,Object> decode(String s){try{return json.readValue(s,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
 public Map<String,Object> read(String email){
  var rows=db.query("SELECT document FROM account_documents WHERE email=?",(rs,n)->decode(rs.getString(1)),email);if(!rows.isEmpty())return rows.getFirst();
  // Lazily preserve accounts created by the original H2 version.
  var old=db.queryForList("SELECT password,profile FROM users WHERE email=?",email);if(old.isEmpty())return null;
  var account=new HashMap<String,Object>();account.put("email",email);account.put("password",old.getFirst().get("PASSWORD"));account.put("profile",decode((String)old.getFirst().get("PROFILE")));
  for(String table:List.of("workouts","plans","wellness"))account.put(table,db.query("SELECT data FROM "+table+" WHERE email=?",(rs,n)->decode(rs.getString(1)),email));
  create(email,account);return read(email);
 }
 public boolean create(String email,Map<String,Object> account){try{db.update("INSERT INTO account_documents(email,document,version) VALUES(?,?,0)",email,encode(account));return true;}catch(DuplicateKeyException e){return false;}}
 public void update(String email,UnaryOperator<Map<String,Object>> change){for(int attempt=0;attempt<5;attempt++){
  read(email);var rows=db.queryForList("SELECT document,version FROM account_documents WHERE email=?",email);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found");var row=rows.getFirst();var updated=change.apply(decode((String)row.get("DOCUMENT")));int n=db.update("UPDATE account_documents SET document=?,version=version+1 WHERE email=? AND version=?",encode(updated),email,row.get("VERSION"));if(n==1)return;
 }throw new ResponseStatusException(HttpStatus.CONFLICT,"This account changed. Please try again.");}
}
