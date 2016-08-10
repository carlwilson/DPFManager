package dpfmanager.conformancechecker.tiff.implementation_checker;

import dpfmanager.conformancechecker.tiff.implementation_checker.model.TiffHeader;
import dpfmanager.conformancechecker.tiff.implementation_checker.model.TiffIfd;
import dpfmanager.conformancechecker.tiff.implementation_checker.model.TiffIfds;
import dpfmanager.conformancechecker.tiff.implementation_checker.model.TiffTag;
import dpfmanager.conformancechecker.tiff.implementation_checker.model.TiffTags;
import dpfmanager.conformancechecker.tiff.implementation_checker.model.TiffValidationObject;

import com.easyinnova.tiff.model.IfdTags;
import com.easyinnova.tiff.model.Metadata;
import com.easyinnova.tiff.model.TagValue;
import com.easyinnova.tiff.model.TiffDocument;
import com.easyinnova.tiff.model.types.IFD;
import com.easyinnova.tiff.model.types.IPTC;
import com.easyinnova.tiff.model.types.abstractTiffType;

import org.jsoup.Connection;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import javax.xml.crypto.dsig.keyinfo.KeyValue;

/**
 * Created by easy on 08/03/2016.
 */
public class TiffImplementationChecker {
  private TiffDocument tiffDoc;
  boolean setITFields = false;
  private Hashtable<Integer, Integer> usedOffsetsSizes;

  public void setITFields(boolean setITFields) {
    this.setITFields = setITFields;
  }

  public TiffValidationObject CreateValidationObject(TiffDocument tiffDoc) {
    this.tiffDoc = tiffDoc;
    TiffValidationObject tiffValidate = new TiffValidationObject();
    usedOffsetsSizes = new Hashtable<>();

    // Generic info
    tiffValidate.setSize(tiffDoc.getSize());

    // Header
    TiffHeader header = new TiffHeader();
    header.setByteOrder(tiffDoc.getEndianess() != null ? tiffDoc.getEndianess().toString() : "");
    header.setMagicNumber(tiffDoc.getMagicNumber() + "");
    header.setOffset(tiffDoc.getFirstIFDOffset());
    tiffValidate.setHeader(header);
    usedOffsetsSizes.put(0, 8);

    // IFDs
    IFD ifd = tiffDoc.getFirstIFD();
    if (ifd != null) {
      usedOffsetsSizes.put(tiffDoc.getFirstIFDOffset(), 4);
    }
    List<TiffIfd> ifdsList = new ArrayList<TiffIfd>();
    int n = 1;
    HashSet<Integer> usedOffsets = new HashSet<>();
    usedOffsets.add(tiffDoc.getFirstIFDOffset());
    boolean circularReference = false;
    while (ifd != null) {
      ifdsList.add(CreateIFDValidation(ifd, n++));
      if (usedOffsets.contains(ifd.getNextOffset())) {
        circularReference = true;
        break;
      } else {
        usedOffsets.add(ifd.getNextOffset());
      }
      if (ifd.getNextIFD() != null) {
        usedOffsetsSizes.put(ifd.getNextOffset(), 4);
      }
      ifd = ifd.getNextIFD();
    }
    TiffIfds ifds = new TiffIfds();
    ifds.setCircularReference(circularReference);
    ifds.setIfds(ifdsList);
    tiffValidate.setIfds(ifds);

    return tiffValidate;
  }

  public TiffIfd CreateIFDValidation(IFD ifd, int n) {
    boolean hasSubIfd = ifd.hasSubIFD();
    boolean thumbnail = hasSubIfd && ifd.getsubIFD().getImageSize() > ifd.getImageSize();
    IfdTags metadata;
    if (!hasSubIfd) {
      metadata = ifd.getMetadata();
    } else if (!thumbnail) {
      metadata = ifd.getMetadata();
    } else {
      metadata = ifd.getsubIFD().getMetadata();
    }

    TiffIfd tiffIfd = new TiffIfd();
    tiffIfd.setN(n);
    List<TiffTag> tags = new ArrayList<TiffTag>();
    int prevTagId = -1;
    boolean correctTagOrdering = true;
    boolean duplicatedTags = false;
    boolean correctCompression = true;
    boolean correctPhotometricCasuistic = true;
    boolean correctYcbcr = true;
    HashSet tagIds = new HashSet<>();
    for (TagValue tv : ifd.getTags().getTags()) {
      try {
        if (tv.getId() <= prevTagId) {
          correctTagOrdering = false;
        }
        if (tagIds.contains(tv.getId())) {
          duplicatedTags = true;
        } else {
          tagIds.add(tv.getId());
        }
        prevTagId = tv.getId();
        tags.add(CreateTiffTag(tv));
        usedOffsetsSizes.put(tv.getReadOffset(), tv.getReadlength());
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    TiffTags tiffTags = new TiffTags();
    tiffTags.setTags(tags);
    tiffIfd.setTags(tiffTags);
    tiffIfd.setTagOrdering(correctTagOrdering ? 1 : 0);
    tiffIfd.setDuplicateTags(duplicatedTags ? 1 : 0);
    tiffIfd.setStrips(ifd.hasStrips() ? 1 : 0);
    tiffIfd.setTiles(ifd.hasTiles() ? 1 : 0);
    tiffIfd.setCorrectStrips(1);
    tiffIfd.setCorrectTiles(1);
    tiffIfd.setOffset(ifd.getNextOffset());

    // Strips check
    if (ifd.hasStrips()) {
      int pixelSize = 0;
      for (int i = 0; i < metadata.get("BitsPerSample").getCardinality(); i++) {
        pixelSize += metadata.get("BitsPerSample").getValue().get(i).toInt();
      }
      if (metadata.get("Compression").getFirstNumericValue() == 1 && pixelSize >= 8) {
        int calculatedImageLength = 0;
        int id = com.easyinnova.tiff.model.TiffTags.getTagId("StripBYTECount");
        int nsc = metadata.get(id).getCardinality();
        for (int i = 0; i < nsc; i++) {
          calculatedImageLength += metadata.get(id).getValue().get(i).toInt();
        }
        if (calculatedImageLength != metadata.get("ImageLength").getFirstNumericValue()
            * metadata.get("ImageWidth").getFirstNumericValue() * pixelSize / 8) {
          tiffIfd.setCorrectStrips(0);
        }
      }
    }

    // Tiles check
    if (ifd.hasTiles()) {
      long tileLength = metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("TileLength")).getFirstNumericValue();;
      long tileWidth = metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("TileWidth")).getFirstNumericValue();;
      long tilesPerImage =
          ((metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("ImageWidth")).getFirstNumericValue() + tileWidth - 1) / tileWidth)
              * ((metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("ImageLength")).getFirstNumericValue() + tileLength - 1) / tileLength);

      // Check Plannar Configuration
      int id = com.easyinnova.tiff.model.TiffTags.getTagId("PlanarConfiguration");
      int idspp = com.easyinnova.tiff.model.TiffTags.getTagId("SamplesPerPixel");
      if (metadata.containsTagId(id) && metadata.containsTagId(idspp)) {
        long planar = metadata.get(id).getFirstNumericValue();
        long spp = metadata.get(idspp).getFirstNumericValue();
        if (planar == 2) {
          long spp_tpi = spp * tilesPerImage;
          if (ifd.getImageTiles().getTiles().size() < spp_tpi) {
            tiffIfd.setCorrectTiles(0);
          }
        }
      }
    }

    // Check pixel samples bits
    if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("BitsPerSample"))
        && metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("SamplesPerPixel"))) {
      boolean correctExtraSamples = true;
      boolean onlyNecessaryExtraSamples = true;
      boolean validBitsPerSample = true;
      boolean equalBitsPerSampleValues = true;
      int bps = metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("BitsPerSample")).getValue().size();

      if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("ExtraSamples"))) {
        int ext = metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("ExtraSamples")).getValue().size();
        if (ext + 3 != bps) {
          correctExtraSamples = false;
        } else if (ext > 0 && bps <= 3) {
          onlyNecessaryExtraSamples = false;
        }
      }

      if (bps > 1) {
        TagValue lbps = metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("BitsPerSample"));
        if (lbps == null || lbps.getValue() == null) {
          validBitsPerSample = false;
        } else {
          boolean distinct_bps_samples = false;
          for (int i = 1; i < lbps.getCardinality(); i++) {
            if (lbps.getValue().get(i).toInt() != lbps.getValue().get(i - 1).toInt())
              distinct_bps_samples = true;
          }
          if (distinct_bps_samples)
            equalBitsPerSampleValues = false;
        }
      }

      // Check correct compression
      if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("Compression")) && metadata.get("Compression").getFirstNumericValue() == 1) {
        if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("CompressedBitsPerPixel"))) {
          correctCompression = false;
        }
      }

      // Check photometric casuistic
      if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation"))) {
        int photo = (int) metadata.get("PhotometricInterpretation").getFirstNumericValue();
        if (photo != 6) {
          if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrCoefficients"))) correctPhotometricCasuistic = false;
          if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrSubSampling"))) correctPhotometricCasuistic = false;
          if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrPositioning"))) correctPhotometricCasuistic = false;
          if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("ReferenceBlackWhite"))) correctPhotometricCasuistic = false;
        }
        long spp = 0;
        if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("SamplesPerPixel")))
          spp = metadata.get("SamplesPerPixel").getFirstNumericValue();
        if (photo == 2 || photo == 3) {
          if (spp != 3) {
            correctPhotometricCasuistic = false;
          }
        } else if (photo == 1 || photo == 32803) {
          if (spp != 1) {
            correctPhotometricCasuistic = false;
          }
          if (photo == 32803) {
            if (!metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("CFARepeatPatternDim"))) correctPhotometricCasuistic = false;
            else if (metadata.get("CFARepeatPatternDim").getCardinality() != 2) correctPhotometricCasuistic = false;
            if (!metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("CFAPattern"))) correctPhotometricCasuistic = false;
          }
        }
      }

      // Check YcbCr
      int nycbcr = 0;
      if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrCoefficients")))
        nycbcr++;
      if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrSubSampling")))
        nycbcr++;
      if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrPositioning")))
        nycbcr++;
      if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("ReferenceBlackWhite")))
        nycbcr++;
      if (nycbcr > 0 && nycbcr != 4) {
        if (!metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("CFARepeatPatternDim"))) correctYcbcr = false;
        else if (metadata.get("CFARepeatPatternDim").getCardinality() != 3) correctPhotometricCasuistic = false;
        if (!metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrSubSampling"))) correctYcbcr = false;
        else if (metadata.get("YCbCrSubSampling").getCardinality() != 2) correctPhotometricCasuistic = false;
        if (!metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrPositioning"))) correctYcbcr = false;
        else if (metadata.get("YCbCrPositioning").getCardinality() != 1) correctPhotometricCasuistic = false;
        if (!metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("ReferenceBlackWhite"))) correctYcbcr = false;
        else if (metadata.get("ReferenceBlackWhite").getCardinality() != 6) correctPhotometricCasuistic = false;
      }
      if (thumbnail) {
        if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrCoefficients"))) correctYcbcr = false;
        if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrSubSampling"))) correctYcbcr = false;
        if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("YCbCrPositioning"))) correctYcbcr = false;
        if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("ReferenceBlackWhite"))) correctYcbcr = false;
      }

      // IT Fields
      if (setITFields) {
        setITFields(ifd, tiffIfd);
      }

      tiffIfd.setCorrectExtraSamples(correctExtraSamples ? 1 : 0);
      tiffIfd.setOnlyNecessaryExtraSamples(onlyNecessaryExtraSamples ? 1 : 0);
      tiffIfd.setValidBitsPerSample(validBitsPerSample ? 1 : 0);
      tiffIfd.setEqualBitsPerSampleValues(equalBitsPerSampleValues ? 1 : 0);
      tiffIfd.setCorrectCompression(correctCompression ? 1 : 0);
      tiffIfd.setCorrectPhotometricCasuistic(correctPhotometricCasuistic ? 1 : 0);
      tiffIfd.setCorrectYcbcr(correctYcbcr ? 1 : 0);
    }

    // Check image type
    if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation"))) {
      int photometric = (int) metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation")).getFirstNumericValue();
      switch (photometric) {
        case 0:
        case 1:
          if (!metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("BitsPerSample"))
              || metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("BitsPerSample")).getFirstNumericValue() == 1) {
            tiffIfd.setType("Bilevel");
          } else {
            tiffIfd.setType("Grayscale");
          }
          break;
        case 2:
          tiffIfd.setType("RGB");
          break;
        case 3:
          tiffIfd.setType("Pallete");
          break;
        case 4:
          tiffIfd.setType("Transparency");
          break;
        case 5:
          tiffIfd.setType("CMYK");
          break;
        case 6:
          tiffIfd.setType("YCbCr");
          break;
        case 8:
        case 9:
        case 10:
          tiffIfd.setType("CIELab");
          break;
      }
    }

    return tiffIfd;
  }

  void setITFields(IFD ifd, TiffIfd tiffIfd) {
    IfdTags metadata = ifd.getMetadata();
    int sft = -1;
    int photo = -1;
    int bps = -1;
    int planar = -1;
    int comp = -1;
    if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("SubfileType"))) {
      sft = (int)metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("SubfileType")).getFirstNumericValue();
    }
    if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("Compression"))) {
      comp = (int)metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("Compression")).getFirstNumericValue();
    }
    if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation"))) {
      photo = (int)metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation")).getFirstNumericValue();
    }
    if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("BitsPerSample"))) {
      bps = (int)metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("BitsPerSample")).getFirstNumericValue();
    }
    if (metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("PlanarConfiguration"))) {
      planar = (int)metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("PlanarConfiguration")).getFirstNumericValue();
    }

    // Determination of TIFF/IT file type
    if (sft == 1 || sft == -1) {
      if (comp == 1 || comp == 32895) {
        if (photo == 5) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 32768) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 2) {
            if (bps > 1) {
              tiffIfd.setFiletype("ct");
            } else if (bps == 1) {
              tiffIfd.setFiletype("sd");
            }
          }
        } else if (photo == 2) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 32768) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 2) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 8) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 32768) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 2) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 0 || photo == 1) {
          if (bps == 1) {
            tiffIfd.setFiletype("bp");
          } else if (bps > 1) {
            tiffIfd.setFiletype("mp");
          }
        }
      } else if (comp == 4) {
        if (photo == 0 || photo == 1) {
          tiffIfd.setFiletype("bp");
        } else if (photo == 5) {
          tiffIfd.setFiletype("sd");
        }
      } else if (comp == 7) {
        if (photo == 5) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 2) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 6) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 8) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 0 || photo == 1) {
          if (bps > 1) {
            tiffIfd.setFiletype("mp");
          }
        }
      } else if (comp == 8) {
        if (photo == 5) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 32768) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 2) {
            if (bps > 1) {
              tiffIfd.setFiletype("ct");
            } else if (bps == 1) {
              tiffIfd.setFiletype("sd");
            }
          }
        } else if (photo == 2) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 32768) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 2) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 8) {
          if (planar == 1) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 32768) {
            tiffIfd.setFiletype("ct");
          } else if (planar == 2) {
            tiffIfd.setFiletype("ct");
          }
        } else if (photo == 0 || photo == 1) {
          if (bps == 1) {
            tiffIfd.setFiletype("bp");
          } else if (bps > 1) {
            tiffIfd.setFiletype("mp");
          }
        }
      } else if (comp == 32896) {
        tiffIfd.setFiletype("lw");
      } else if (comp == 32897) {
        tiffIfd.setFiletype("hc");
      } else if (comp == 32898) {
        tiffIfd.setFiletype("bp");
      } else if (((sft >> 3) & 1) == 1) {
        tiffIfd.setFiletype("fp");
      }
    }

    if (tiffIfd.getFiletype().equals("ct")) {
      boolean rgb =
          metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation"))
              && metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation")).getFirstNumericValue() == 2;
      boolean lab =
          metadata.containsTagId(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation"))
              && metadata.get(com.easyinnova.tiff.model.TiffTags.getTagId("PhotometricInterpretation")).getFirstNumericValue() == 8;
      if (rgb) {
        tiffIfd.setImgtype("rgb");
      } else if (lab) {
        tiffIfd.setImgtype("lab");
      } else {
        tiffIfd.setImgtype("norgblab");
      }
    }
  }

  TiffIfd createIfdNode(TagValue tv, String nodeName) {
    TiffIfd ifd = new TiffIfd();
    List<TiffTag> tags = new ArrayList<TiffTag>();
    int prevTagId = -1;
    boolean correctTagOrdering = true;
    boolean duplicatedTags = false;
    HashSet tagIds = new HashSet<>();
    IFD exif = (IFD) tv.getValue().get(0);
    for (TagValue tvv : exif.getTags().getTags()) {
      if (tvv.getId() <= prevTagId) {
        correctTagOrdering = false;
      }
      if (tagIds.contains(tvv.getId())) {
        duplicatedTags = true;
      } else {
        tagIds.add(tvv.getId());
      }
      prevTagId = tvv.getId();
      tags.add(CreateTiffTag(tvv));
    }
    TiffTags tiffTags = new TiffTags();
    tiffTags.setTags(tags);
    ifd.setTags(tiffTags);
    ifd.setTagOrdering(correctTagOrdering ? 1 : 0);
    ifd.setDuplicateTags(duplicatedTags ? 1 : 0);
    ifd.setClassElement(nodeName);
    return ifd;
  }

  boolean checkOffsetOverlapped(int offset, int length) {
    for (Integer usedOffset : usedOffsetsSizes.keySet()) {
      int size = usedOffsetsSizes.get(usedOffset);
      if (offset >= usedOffset && offset < usedOffset + size)
        return true;
      if (offset + length >= usedOffset && offset + length < usedOffset + size)
        return true;
    }
    return false;
  }

  public TiffTag CreateTiffTag(TagValue tv) {
    TiffTag tt = new TiffTag();
    tt.setId(tv.getId());
    tt.setName(tv.getName());
    tt.setCardinality(tv.getCardinality());
    tt.setType(com.easyinnova.tiff.model.TiffTags.getTagTypeName(tv.getType()));
    try {
      if (tt.getType().equals("ASCII")) {
        boolean ascii7ok = true;
          for (abstractTiffType a : tv.getValue()) {
            boolean isSet = (a.toByte() & (1 << 8)) != 0;
            if (isSet) {
              ascii7ok = false;
              break;
            }
          }
        tt.setAsci7(ascii7ok);
      }
    } catch (Exception ex) {

    }
    tt.setOffset(tv.getReadOffset());
    if (usedOffsetsSizes.get(tv.getReadOffset()) != null) {
      tt.setUsedOffset(true);
    } else {
      tt.setOffsetOverlap(checkOffsetOverlapped(tv.getReadOffset(), tv.getReadlength()));
      usedOffsetsSizes.put(tv.getReadOffset(), tv.getReadlength());
    }
    if (tt.getType() != null && tt.getType().equals("ASCII")) {
      if (tv.getCardinality() > 0) {
        tt.setLastByte(tv.getValue().get(tv.getCardinality() - 1).toByte());
        boolean duplicatedNuls = false;
        for (int i=1;i<tv.getCardinality();i++) {
          if (tv.getValue().get(i).toByte() == 0 && tv.getValue().get(i-1).toByte() == 0) {
            duplicatedNuls = true;
          }
        }
        tt.setDuplicatedNuls(duplicatedNuls);
      }
    }
    if (tv.getId() == 34665) {
      // EXIF
      TiffIfd ifd = createIfdNode(tv, "exif");
      tt.setExif(ifd);
    } else if (tv.getId() == 330) {
      // SubIFD
      TiffIfd ifd = createIfdNode(tv, "subifd");
      tt.setSubIfd(ifd);
    } else if (tv.getId() == 400) {
      // GlobalParametersIFD
      TiffIfd ifd = createIfdNode(tv, "globalparameters");
      tt.setGlobalParameters(ifd);
    } else if (tv.getId() == 700) {
      // XMP
    } else if (tv.getId() == 33723) {
      // IPTC
      IPTC iptc = (IPTC)tv.getValue().get(0);
      Hashtable<String, String> keyvalues = new Hashtable<String, String>();
      Metadata meta = iptc.createMetadata();
      for (String key : meta.keySet())
      {
        keyvalues.put(key, meta.get(key).toString());
      }
      tt.setIptc(keyvalues);
    } else {
      tt.setValue(tv.toString().replaceAll("\\p{C}", "?"));
    }
    return tt;
  }
}
