package dpfmanager.shell.conformancechecker;

import dpfmanager.shell.interfaces.UserInterface;
import dpfmanager.shell.reporting.IndividualReport;
import dpfmanager.shell.reporting.ReportGenerator;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import com.easyinnova.tiff.model.ReadIccConfigIOException;
import com.easyinnova.tiff.model.ReadTagsIOException;
import com.easyinnova.tiff.model.TiffDocument;
import com.easyinnova.tiff.model.ValidationResult;
import com.easyinnova.tiff.reader.TiffReader;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by easy on 04/09/2015.
 */
public class ProcessInput {
  private ReportGenerator reportGenerator;
  public boolean outOfmemory = false;
  private List<String> allowedExtensions;
  private boolean checkBL, checkEP, checkPC;
  private int checkIT;
  private Scene scene;
  private int idReport;

  /**
   * Instantiates a new Process input.
   *
   * @param allowedExtensions the allowed extensions
   */
  public ProcessInput(List<String> allowedExtensions) {
    this.allowedExtensions = allowedExtensions;
  }

  /**
   * Sets the scene.
   *
   * @param scene the scene
   */
  public void setScene(Scene scene) {
    this.scene = scene;
  }

  /**
   * Process files string.
   *
   * @param files        the files
   * @param config       the config
   * @return the string
   */
  public String ProcessFiles(ArrayList<String> files, Configuration config, boolean silence) {
    checkBL = config.getIsos().contains("Baseline");
    checkEP = config.getIsos().contains("Tiff/EP");
    checkIT = -1;
    if (config.getIsos().contains("Tiff/IT")) checkIT = 0;
    if (config.getIsos().contains("Tiff/IT-1")) checkIT = 1;
    if (config.getIsos().contains("Tiff/IT-2")) checkIT = 2;
    if (config.getRules() != null){
      checkPC = config.getRules().getRules().size() > 0;
    } else{
      checkPC = false;
    }

    reportGenerator = new ReportGenerator();
    reportGenerator.setReportsFormats(config.getFormats());
    reportGenerator.setRules(config.getRules());
    reportGenerator.setFixes(config.getFixes());

    // Process files
    ArrayList<IndividualReport> individuals = new ArrayList<IndividualReport>();
    String internalReportFolder = ReportGenerator.createReportPath();
    int n=files.size();
    idReport=1;
    for (final String filename : files) {
      System.out.println("");
      System.out.println("Processing file " + filename);
      List<IndividualReport> indReports = processFile(filename, internalReportFolder, config.getOutput());
      if (scene != null) {
        Platform.runLater(() -> ((Label) scene.lookup("#lblLoading")).setText("Processing..." + (files.indexOf(filename)+1) + "/" + n));
      }
      if (indReports.size() > 0) {
        individuals.addAll(indReports);
      }
      idReport++;
    }

    // Global report
    String summaryXml = null;
    try {
      summaryXml = reportGenerator.makeSummaryReport(internalReportFolder, individuals, config.getOutput(), silence);
    } catch (OutOfMemoryError e) {
      System.err.println("Out of memory.");
      outOfmemory = true;
    }

    // Send report over FTP (only for alpha testing)
    try {
      if(UserInterface.getFeedback() && summaryXml != null) {
        sendFtpCamel(summaryXml);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return internalReportFolder;
  }

  /**
   * Process a Tiff file.
   *
   * @param filename the filename
   * @param internalReportFolder the internal report folder
   * @return the list
   */
  private List<IndividualReport> processFile(String filename, String internalReportFolder, String outputFolder) {
    List<IndividualReport> indReports = new ArrayList<IndividualReport>();
    IndividualReport ir = null;
    if (filename.toLowerCase().endsWith(".zip") || filename.toLowerCase().endsWith(".rar")) {
      // Zip File
      try {
        System.err.println(filename);
        ZipFile zipFile = new ZipFile(filename);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (isTiff(entry.getName())) {
            InputStream stream = zipFile.getInputStream(entry);
            String filename2 = createTempFile(internalReportFolder, entry.getName(), stream);
            ir = processTiffFile(filename2, entry.getName(), internalReportFolder, outputFolder);
            if (ir != null) {
              indReports.add(ir);
            }
            else{
              outOfmemory = true;
              break;
            }
            File file = new File(filename2);
            file.delete();
          }
        }
        zipFile.close();
      } catch (Exception ex) {
        System.err.println("Error reading zip file [" + ex.toString() + "]");
      }
    } else if (isUrl(filename)) {
      // URL
      try {
        if (isTiff(filename)) {
          InputStream input = new java.net.URL(filename).openStream();
          String filename2 = createTempFile(internalReportFolder, new File(filename).getName(), input);
          filename = java.net.URLDecoder.decode(filename, "UTF-8");
          ir = processTiffFile(filename2, filename, internalReportFolder, outputFolder);
          if (ir != null) {
            indReports.add(ir);
          }
          else{
            outOfmemory = true;
          }
          File file = new File(filename2);
          file.delete();
        } else {
          System.err.println("The file in the URL " + filename + " is not a Tiff");
        }
      } catch (Exception ex) {
        System.out.println("Error in URL " + filename);
      }
    } else if (isTiff(filename)) {
      // File
      try {
        ir = processTiffFile(filename, filename, internalReportFolder, outputFolder);
        if (ir != null) {
          indReports.add(ir);
        }
        else{
          outOfmemory = true;
        }
      } catch (Exception ex) {
        System.err.println("Error in File " + filename);
      }
    } else {
      // Anything else
      System.err.println("File " + filename + " is not a Tiff");
    }
    return indReports;
  }

  /**
   * Creates the temp file.
   *
   * @param name the name
   * @param stream the stream
   * @return the string
   * @throws IOException Signals that an I/O exception has occurred.
   */
  private String createTempFile(String folder, String name, InputStream stream) throws IOException {
    String filename2 = "x" + name;
    if (filename2.contains("/")) {
      filename2 = filename2.substring(filename2.lastIndexOf("/") + 1);
    }
    while (new File(filename2).isFile()) {
      filename2 = "x" + filename2;
    }
    filename2 = folder + "/" + filename2;
    File targetFile = new File(filename2);
    OutputStream outStream = new FileOutputStream(targetFile);
    byte[] buffer = new byte[8 * 1024];
    int bytesRead;
    while ((bytesRead = stream.read(buffer)) != -1) {
      outStream.write(buffer, 0, bytesRead);
    }
    outStream.close();
    return filename2;
  }

  /**
   * Process tiff file.
   *
   * @param pathToFile the path in local disk to the file
   * @param reportFilename the file name that will be displayed in the report
   * @param internalReportFolder the internal report folder
   * @param outputFolder the output report folder (optional)
   * @return the individual report
   * @throws ReadTagsIOException the read tags io exception
   * @throws ReadIccConfigIOException the read icc config io exception
   */
  private IndividualReport processTiffFile(String pathToFile, String reportFilename,
                                           String internalReportFolder, String outputFolder) throws ReadTagsIOException, ReadIccConfigIOException {
    try {
      TiffReader tr = new TiffReader();
      int result = tr.readFile(pathToFile);
      switch (result) {
        case -1:
          System.out.println("File '" + pathToFile + "' does not exist");
          break;
        case -2:
          System.out.println("IO Exception in file '" + pathToFile + "'");
          break;
        case 0:
          TiffDocument to = tr.getModel();

          ValidationResult baselineVal = null;
          if (checkBL) baselineVal = tr.getBaselineValidation();
          ValidationResult epValidation = null;
          if (checkEP) epValidation = tr.getTiffEPValidation();
          ValidationResult itValidation = null;
          if (checkIT >= 0) itValidation = tr.getTiffITValidation(checkIT);
          String pathNorm = reportFilename.replaceAll("\\\\", "/");
          String name = pathNorm.substring(pathNorm.lastIndexOf("/") + 1);
          IndividualReport ir = new IndividualReport(name, pathToFile, to, baselineVal, epValidation, itValidation);
          ir.checkBL = checkBL;
          ir.checkEP = checkEP;
          ir.checkIT = checkIT;
          ir.checkPC = checkPC;

          // Generate individual report
          String outputfile = ReportGenerator.getReportName(internalReportFolder, reportFilename, idReport);
          reportGenerator.generateIndividualReport(outputfile, ir, outputFolder);
          System.out.println("Internal report '" + outputfile + "' created");

          to=null;
          tr=null;
          System.gc();
          return ir;
        default:
          System.out.println("Unknown result (" + result + ") in file '" + pathToFile + "'");
          break;
      }
    } catch (ReadTagsIOException e) {
      System.err.println("Error loading Tiff library dependencies");
    } catch (ReadIccConfigIOException e) {
      System.err.println("Error loading Tiff library dependencies");
    } catch (OutOfMemoryError error){
      System.err.println("Out of memory");
    }
    return null;
  }

  /**
   * Checks if is url.
   *
   * @param filename the filename
   * @return true, if is url
   */
  private boolean isUrl(String filename) {
    boolean ok = true;
    try {
      new java.net.URL(filename);
    } catch (Exception ex) {
      ok = false;
    }
    return ok;
  }

  /**
   * Checks if is tiff.
   *
   * @param filename the filename
   * @return true, if is tiff
   */
  private boolean isTiff(String filename) {
    boolean isTiff = false;
    for (String extension : allowedExtensions) {
      if (filename.toLowerCase().endsWith(extension.toLowerCase())) {
        isTiff = true;
      }
    }
    return isTiff;
  }

  /**
   * Send ftp.
   *
   * @param reportGenerator the report generator
   * @param summaryXml the summary xml
   * @throws NoSuchAlgorithmException the no such algorithm exception
   */
  private void sendFtpCamel(String summaryXml)
      throws NoSuchAlgorithmException {
    String ftp = "84.88.145.109";
    String user = "preformaapp";
    String password = "2.eX#lh>";

    CamelContext context = new DefaultCamelContext();
    try {
      context.addRoutes(new RouteBuilder() {
        public void configure() {
          from("direct:sendFtp").to("sftp://" + user + "@" + ftp + "/?password=" + password);
        }
      });
      ProducerTemplate template = context.createProducerTemplate();
      context.start();
      template.sendBody("direct:sendFtp", summaryXml);
      context.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}