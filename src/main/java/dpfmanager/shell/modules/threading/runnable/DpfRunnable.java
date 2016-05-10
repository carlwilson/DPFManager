package dpfmanager.shell.modules.threading.runnable;

import dpfmanager.shell.core.config.BasicConfig;
import dpfmanager.shell.core.context.DpfContext;
import dpfmanager.shell.modules.messages.messages.ExceptionMessage;
import dpfmanager.shell.modules.messages.messages.LogMessage;

import org.apache.logging.log4j.Level;

/**
 * Created by Adrià Llorens on 13/04/2016.
 */
public abstract class DpfRunnable implements Runnable {

  private boolean paused;
  private boolean canceled;
  protected boolean interrupted;
  private boolean init;
  private boolean finish;


  protected String name;
  protected DpfContext context;
  private Long uuid;

  public DpfRunnable() {
    name = "";
    paused = false;
    canceled = false;
    interrupted = false;

    init = false;
    finish = false;
  }

  public void setName(String n){
    name = n;
    name = name.substring(name.lastIndexOf(".")+1);
  }

  /**
   * Main Run task
   */
  @Override
  public void run() {
    init = true;
    runTask();
    finish = true;
  }

  /**
   * Setters & Getters
   */
  public void setContext(DpfContext c){
    context = c;
    handleContext(context);
  }

  public Long getUuid() {
    return uuid;
  }

  public void setUuid(Long uuid) {
    this.uuid = uuid;
  }

  public boolean isFinish() {
    return finish;
  }

  public boolean isInit() {
    return init;
  }

  /**
   * Reimplemented
   */
  public abstract void runTask();

  public abstract void handleContext(DpfContext context);

  /**
   * Thread management
   */
  public synchronized void pause() {
    paused = true;
  }

  public synchronized void resume() {
    paused = false;
    notify();
  }

  public synchronized void cancel() {
    canceled = true;
  }

  public boolean isPaused() {
    return paused;
  }

  /**
   * Custom print lines
   */
  protected void printOut(String message){
    context.send(BasicConfig.MODULE_MESSAGE, new LogMessage(getClass(), Level.DEBUG, message));
  }

  protected void printErr(String message){
    context.send(BasicConfig.MODULE_MESSAGE, new LogMessage(getClass(), Level.ERROR, message));
  }

  protected void printException(String header, Exception ex){
    context.send(BasicConfig.MODULE_MESSAGE, new ExceptionMessage(header, ex));
  }

}