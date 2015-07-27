/**
 * <h1>ReportGenerator.java</h1>
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version; or, at your choice, under the terms of the
 * Mozilla Public License, v. 2.0. SPDX GPL-3.0+ or MPL-2.0+.
 * </p>
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License and the Mozilla Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License and the Mozilla Public License
 * along with this program. If not, see <a
 * href="http://www.gnu.org/licenses/">http://www.gnu.org/licenses/</a> and at <a
 * href="http://mozilla.org/MPL/2.0">http://mozilla.org/MPL/2.0</a> .
 * </p>
 * <p>
 * NB: for the © statement, include Easy Innova SL or other company/Person contributing the code.
 * </p>
 * <p>
 * © 2015 Easy Innova, SL
 * </p>
 *
 * @author Adrià Llorens Martinez
 * @version 1.0
 * @since 23/6/2015
 */

package dpfmanager;

import com.easyinnova.tiff.model.TiffDocument;
import com.easyinnova.tiff.model.ValidationEvent;
import com.easyinnova.tiff.model.ValidationResult;
import com.easyinnova.tiff.model.types.IFD;

import java.util.ArrayList;
import java.util.List;

/**
 * The Class IndividualReport.
 */
public class IndividualReport {

  /* The file name */
  private String filename;

  /* The ifdNode count */
  private int ifdCount;

  /* The isimg list */
  private List<Boolean> listIsimg;

  /* The hasSubIfd list */
  private List<Boolean> listHasSubIfd;

  /* The Tiff width */
  private String width;

  /* The Tiff height */
  private String height;

  /* The errors list */
  private List<ValidationEvent> errors;

  /* The warning list */
  private List<ValidationEvent> warnings;

  /* The Tiff Document object */
  private TiffDocument tiffModel;

  /**
   * Default constructor.
   *
   */
  public IndividualReport() {
    filename = "";
    ifdCount = 0;
    listIsimg = new ArrayList<Boolean>();
    listHasSubIfd = new ArrayList<Boolean>();
  }

  /**
   * Constructor + generate.
   *
   */
  public IndividualReport(String name, TiffDocument tiffModel, ValidationResult validation) {
    filename = name;
    ifdCount = 0;
    listIsimg = new ArrayList<Boolean>();
    listHasSubIfd = new ArrayList<Boolean>();
    generate(tiffModel, validation);
  }

  /**
   * Set file name.
   *
   */
  public void setFileName(String name) {
    filename = name;
  }

  /**
   * Get file name.
   *
   * @return filename
   */
  public String getFileName() {
    return filename;
  }

  /**
   * Saves the ifd node.
   *
   * @param ifd the ifd
   */
  private void saveIfdNode(IFD ifd) {
    listIsimg.add(ifd.isImage());
    listHasSubIfd.add(ifd.getsubIFD() != null);
    ifdCount++;
  }

  /**
   * Generate the report information.
   *
   */
  public void generate(TiffDocument tiffModel, ValidationResult validation) {
    this.tiffModel = tiffModel;
    // tiff structure
    IFD ifd = tiffModel.getFirstIFD();
    if (ifd != null) {
      saveIfdNode(ifd);
      while (ifd.hasNextIFD()) {
        ifd = ifd.getNextIFD();
        saveIfdNode(ifd);
      }
    }

    // basic info
    width = tiffModel.getMetadataSingleString("ImageWidth");
    height = tiffModel.getMetadataSingleString("ImageLength");

    // errors & warnings
    errors = validation.getErrors();
    warnings = validation.getWarnings();
  }

  /**
   * Get ifd count.
   *
   * @return the numbr of ifd's
   */
  public int getIfdCount() {
    return ifdCount;
  }

  /**
   * Get isimg at index.
   *
   * @param index the index
   * @return the isimg list
   */
  public Boolean getIsimgAt(int index) {
    return listIsimg.get(index);
  }

  /**
   * Get hasSubIfd at index.
   *
   * @param index the index
   * @return the list of hasSubIfd
   */
  public Boolean getHasSubIfdAt(int index) {
    return listHasSubIfd.get(index);
  }

  /**
   * Get width.
   *
   * @return the width
   */
  public String getWidth() {
    return width;
  }

  /**
   * Get height.
   *
   * @return the height
   */
  public String getHeight() {
    return height;
  }

  /**
   * Get errors list.
   *
   * @return the errors
   */
  public List<ValidationEvent> getErrors() {
    return errors;
  }

  /**
   * Get warnings list.
   *
   * @return the warnings
   */
  public List<ValidationEvent> getWarnings() {
    return warnings;
  }

  /**
   * Get Tiff Model.
   *
   * @return the tiffModel
   */
  public TiffDocument getTiffModel() {
    return tiffModel;
  }

}