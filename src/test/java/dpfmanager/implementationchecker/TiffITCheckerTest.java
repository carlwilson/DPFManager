package dpfmanager.implementationchecker;

import static java.io.File.separator;

import dpfmanager.conformancechecker.tiff.ImplementationChecker.TiffImplementationChecker;
import dpfmanager.conformancechecker.tiff.ImplementationChecker.Validator;
import dpfmanager.conformancechecker.tiff.ImplementationChecker.model.TiffValidationObject;
import dpfmanager.conformancechecker.tiff.ImplementationChecker.rules.RuleResult;

import com.easyinnova.tiff.model.TiffDocument;
import com.easyinnova.tiff.model.ValidationResult;
import com.easyinnova.tiff.reader.TiffReader;

import junit.framework.TestCase;

import java.io.File;
import java.util.List;

/**
 * Created by easy on 23/03/2016.
 */
public class TiffITCheckerTest extends TestCase {
  void testValid(String filename, int profile) throws Exception {
    String outfilename = "file.xml";

    TiffReader tr = new TiffReader();
    int result = tr.readFile(filename);
    assertEquals(0, result);

    TiffDocument td = tr.getModel();
    TiffImplementationChecker tic = new TiffImplementationChecker();
    tic.setITFields(true);
    TiffValidationObject tiffValidation = tic.CreateValidationObject(td);
    tiffValidation.writeXml(outfilename);

    Validator v = new Validator();
    if (profile == 0)
      v.validateTiffIT(outfilename);
    else if (profile == 1)
      v.validateTiffITP1(outfilename);
    else
      v.validateTiffITP2(outfilename);
    List<RuleResult> results = v.getErrors();

    ValidationResult validation = tr.getTiffITValidation(profile);
    assertEquals(0, results.size());
    assertEquals(validation.getErrors().size(), results.size());

    new File(outfilename).delete();
  }

  void testInvalid(String filename, int profile, int errors) throws Exception {
    String outfilename = "file.xml";

    TiffReader tr = new TiffReader();
    int result = tr.readFile(filename);
    assertEquals(0, result);

    TiffDocument td = tr.getModel();
    TiffImplementationChecker tic = new TiffImplementationChecker();
    tic.setITFields(true);
    TiffValidationObject tiffValidation = tic.CreateValidationObject(td);
    tiffValidation.writeXml(outfilename);

    Validator v = new Validator();
    if (profile == 0)
      v.validateTiffIT(outfilename);
    else if (profile == 1)
      v.validateTiffITP1(outfilename);
    else
      v.validateTiffITP2(outfilename);
    List<RuleResult> results = v.getErrors();

    if (errors > 0)
      assertEquals(errors, results.size());
    else
      assertEquals(true, results.size() > 0);

    new File(outfilename).delete();
  }

  public void testValidTestP0Valid() throws Exception {
    testValid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "sample-cmyk-IT.tif", 0);
  }

  public void testValidTestP0Invalid() throws Exception {
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "sample-IT.tif", 0, 1);
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "IMG_0887_EP.tif", 0, 2);
  }

  public void testValidTestP1Valid() throws Exception {
  }

  public void testValidTestP1Invalid() throws Exception {
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "sample-IT.tif", 1, -1);
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "IMG_0887_EP.tif", 1, -1);
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "sample-cmyk-IT.tif", 1, -1);
  }

  public void testValidTestP2Valid() throws Exception {
  }

  public void testValidTestP2Invalid() throws Exception {
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "sample-IT.tif", 2, -1);
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "IMG_0887_EP.tif", 2, -1);
    testInvalid("src" + separator + "test" + separator + "resources" + separator
        + "IT Samples" + separator + "sample-cmyk-IT.tif", 2, -1);
  }
}