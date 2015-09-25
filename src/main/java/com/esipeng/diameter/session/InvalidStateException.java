package com.esipeng.diameter.session;

public class InvalidStateException extends Exception {
  public InvalidStateException() {}
  
  public InvalidStateException(String paramString) {
    super(paramString);
  }
  
  public InvalidStateException(Throwable paramThrowable) { super(paramThrowable); }
}

