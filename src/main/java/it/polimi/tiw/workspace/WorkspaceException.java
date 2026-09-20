package it.polimi.tiw.workspace;

public class WorkspaceException extends RuntimeException {
  private final int status;

  public WorkspaceException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
