package com.sportfit;
import java.util.Map;
import java.util.function.UnaryOperator;
public interface AccountStore {
 Map<String,Object> read(String email);
 boolean create(String email, Map<String,Object> account);
 void update(String email, UnaryOperator<Map<String,Object>> change);
}
