package dpfmanager.shell.modules.threading.core;

import dpfmanager.shell.core.DpFManagerConstants;
import dpfmanager.shell.core.adapter.DpfService;
import dpfmanager.shell.core.config.BasicConfig;
import dpfmanager.shell.core.config.GuiConfig;
import dpfmanager.shell.core.context.DpfContext;
import dpfmanager.shell.core.messages.ReportsMessage;
import dpfmanager.shell.interfaces.gui.workbench.DpfCloseEvent;
import dpfmanager.shell.interfaces.gui.workbench.GuiWorkbench;
import dpfmanager.shell.modules.database.messages.DatabaseMessage;
import dpfmanager.shell.modules.messages.messages.CloseMessage;
import dpfmanager.shell.modules.messages.messages.ExceptionMessage;
import dpfmanager.shell.modules.messages.messages.LogMessage;
import dpfmanager.shell.modules.report.core.IndividualReport;
import dpfmanager.shell.modules.report.messages.GlobalReportMessage;
import dpfmanager.shell.modules.threading.messages.GlobalStatusMessage;
import dpfmanager.shell.modules.threading.messages.RunnableMessage;
import dpfmanager.shell.modules.threading.messages.ThreadsMessage;
import dpfmanager.shell.modules.threading.runnable.DpfRunnable;
import dpfmanager.shell.modules.timer.messages.TimerMessage;
import dpfmanager.shell.modules.timer.tasks.JobsStatusTask;
import javafx.stage.WindowEvent;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Created by Adrià Llorens on 07/04/2016.
 */
@Service(BasicConfig.SERVICE_THREADING)
@Scope("singleton")
public class ThreadingService extends DpfService {

  /**
   * The main executor service
   */
  private DpfExecutor myExecutor;

  /**
   * The number of threads
   */
  private int cores;

  private Map<Long, FileCheck> checks;
  private Queue<FileCheck> pendingChecks;

  private boolean needReload;

  @PostConstruct
  public void init() {
    // No context yet
    checks = new HashMap<>();
    pendingChecks = new PriorityQueue<>();
    needReload = true;
  }

  @PreDestroy
  public void finish() {
    // Finish executor
    myExecutor.shutdownNow();
  }

  @Override
  protected void handleContext(DpfContext context) {
    cores = Runtime.getRuntime().availableProcessors() - 1;
    if (cores < 1) {
      cores = 1;
    }

    // Check for the -t option for tests
    if (GuiWorkbench.getAppParams() != null) {
      for (String param : GuiWorkbench.getAppParams().getRaw()) {
        if (param.startsWith("-t") && param.length() == 3) {
          String number = param.substring(2);
          int threads = Integer.valueOf(number);
          if (threads < cores) {
            cores = threads;
          }
          break;
        }
      }
    }

    myExecutor = new DpfExecutor(cores);
    myExecutor.handleContext(context);
  }

  public void run(DpfRunnable runnable, Long uuid) {
    runnable.setContext(getContext());
    runnable.setUuid(uuid);
    myExecutor.myExecute(runnable);
  }

  public void processThreadMessage(ThreadsMessage tm) {
    if (tm.isPause() && tm.isRequest()) {
      myExecutor.pause(tm.getUuid());
    } else if (tm.isResume()) {
      context.send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.RESUME, tm.getUuid()));
      myExecutor.resume(tm.getUuid());
    } else if (tm.isCancel() && tm.isRequest()) {
      myExecutor.cancel(tm.getUuid());
    } else if (tm.isCancel() && !tm.isRequest()){
      cancelFinish(tm.getUuid());
    } else if (tm.isPause() && !tm.isRequest()){
      pauseFinish(tm.getUuid());
    }
  }

  public void closeRequested(){
    context.send(BasicConfig.MODULE_MESSAGE, new CloseMessage(!checks.isEmpty()));
  }

  public void cancelFinish(Long uuid) {
    // Update db
    getContext().send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.CANCEL, uuid));

    // Remove folder
    removeInternalFolder(checks.get(uuid).getInternal());

    // Remove from checks pool
    checks.remove(uuid);
  }

  public void pauseFinish(Long uuid) {
    // Update db
    getContext().send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.PAUSE, uuid));
    // Refresh tasks
    context.send(BasicConfig.MODULE_TIMER, new TimerMessage(TimerMessage.Type.RUN, JobsStatusTask.class));
  }

  public void handleGlobalStatus(GlobalStatusMessage gm, boolean silence) {
    if (gm.isNew()) {
      // New file check
      Long uuid = System.currentTimeMillis();
      FileCheck fc = new FileCheck(uuid);
      boolean pending = false;
      if (runningChecks() >= DpFManagerConstants.MAX_CHECKS) {
        // Add pending check
        fc.setInitialTask(gm.getRunnable());
        pendingChecks.add(fc);
        pending = true;
      } else {
        //Start now
        checks.put(uuid, fc);
        context.send(BasicConfig.MODULE_THREADING, new RunnableMessage(uuid, gm.getRunnable()));
      }
      context.send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.NEW, uuid, gm.getInput(), pending));
    } else if (gm.isInit()) {
      // Init file check
      FileCheck fc = checks.get(gm.getUuid());
      fc.init(gm.getSize(), gm.getConfig(), gm.getInternal(), gm.getInput());
      context.send(BasicConfig.MODULE_MESSAGE, new LogMessage(getClass(), Level.DEBUG, "Starting check: " + gm.getInput()));
      context.send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.INIT, fc.getUuid(), fc.getTotal(), fc.getInternal()));
    } else if (gm.isFinish()) {
      // Finish file check
      FileCheck fc = checks.get(gm.getUuid());
      removeZipFolder(fc.getInternal());
      removeDownloadFolder(fc.getInternal());
      if (context.isGui()) {
        // Notify task manager
        needReload = true;
      } else if (!silence) {
        // No ui, show to user
        showToUser(fc.getInternal(), fc.getConfig().getOutput());
      }
      context.send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.FINISH, gm.getUuid()));
      checks.remove(gm.getUuid());
      // Start pending checks
      startPendingChecks();
    } else if (context.isGui() && gm.isReload()) {
      // Ask for reload
      if (needReload) {
        needReload = false;
        context.send(GuiConfig.PERSPECTIVE_REPORTS + "." + GuiConfig.COMPONENT_REPORTS, new ReportsMessage(ReportsMessage.Type.RELOAD));
      }
    }
  }

  synchronized public void finishIndividual(IndividualReport ir, Long uuid) {
    FileCheck fc = checks.get(uuid);
    if (fc != null) {
      if (ir != null) {
        // Individual report finished
        fc.addIndividual(ir);

        // Check if all finished
        if (fc.allFinished()) {
          // Tell reports module
          context.send(BasicConfig.MODULE_REPORT, new GlobalReportMessage(uuid, fc.getIndividuals(), fc.getConfig()));
        }
      } else {
        // Individual with errors
        fc.addError();
      }
      context.send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.UPDATE, uuid));
    }
  }

  private void startPendingChecks() {
    if (!pendingChecks.isEmpty()) {
      FileCheck fc = pendingChecks.poll();
      context.send(BasicConfig.MODULE_DATABASE, new DatabaseMessage(DatabaseMessage.Type.START, fc.getUuid()));
      context.send(BasicConfig.MODULE_THREADING, new RunnableMessage(fc.getUuid(), fc.getInitialTask()));
    }
  }

  private int runningChecks() {
    return checks.size();
  }

  /**
   * Remove functions
   */
  public void removeZipFolder(String internal) {
    try {
      File zipFolder = new File(internal + "zip");
      if (zipFolder.exists() && zipFolder.isDirectory()) {
        FileUtils.deleteDirectory(zipFolder);
      }
    } catch (Exception e) {
      context.send(BasicConfig.MODULE_MESSAGE, new ExceptionMessage("Exception in remove zip", e));
    }
  }

  public void removeDownloadFolder(String internal) {
    try {
      File zipFolder = new File(internal + "download");
      if (zipFolder.exists() && zipFolder.isDirectory()) {
        FileUtils.deleteDirectory(zipFolder);
      }
    } catch (Exception e) {
      context.send(BasicConfig.MODULE_MESSAGE, new ExceptionMessage("Exception in remove zip", e));
    }
  }

  public void removeInternalFolder(String internal) {
    try {
      File folder = new File(internal);
      if (folder.exists() && folder.isDirectory()) {
        FileUtils.deleteDirectory(folder);
      }
    } catch (Exception e) {
      context.send(BasicConfig.MODULE_MESSAGE, new ExceptionMessage("Exception in remove internal folder", e));
    }
  }

  /**
   * Show report
   */
  private void showToUser(String internal, String output) {
    String name = "report.html";
    String htmlPath = internal + name;
    if (output != null) {
      htmlPath = output + "/" + name;
    }
    File htmlFile = new File(htmlPath);
    if (htmlFile.exists() && Desktop.isDesktopSupported()) {
      try {
        String fullHtmlPath = htmlFile.getAbsolutePath();
        fullHtmlPath = fullHtmlPath.replaceAll("\\\\", "/");
        Desktop.getDesktop().browse(new URI("file:///" + fullHtmlPath.replaceAll(" ", "%20")));
      } catch (Exception e) {
        context.send(BasicConfig.MODULE_MESSAGE, new ExceptionMessage("Error opening the bowser with the global report.", e));
      }
    } else {
      context.send(BasicConfig.MODULE_MESSAGE, new LogMessage(getClass(), Level.DEBUG, "Desktop services not suported."));
    }
  }

}