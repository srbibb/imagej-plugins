import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.io.DirectoryChooser;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;

import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.*;

public class Sarah_Mitotic_Threshold implements PlugIn {

    RoiManager roiManager;

    public void run(String args) {
        //IJ.run("Bio-Formats Importer");// Open new file;

        //Choose directory
        roiManager = new RoiManager();
        DirectoryChooser dc = new DirectoryChooser("Bio-Formats Mass Importer");
        String baseDirectory = dc.getDirectory();
        dc = new DirectoryChooser("Output Folder");
        String outputDirectory = dc.getDirectory();

        // list of files to actually open with Bio-Formats Importer
        ArrayList<String> filesToOpen = new ArrayList<>();

        // process all files in the chosen directory
        File dir = new File(baseDirectory);
        File[] files = dir.listFiles();

        for (int m = 0; m < files.length; m++) {
            filesToOpen.add(files[m].getPath());
            String id = filesToOpen.get(m);
            if ((id.contains(".ims")) && !(id.contains("xml"))) {
                roiManager.reset();
                IJ.run("Bio-Formats Importer", "open=" + id + " color_mode=Default open_files view=Hyperstack stack_order=XYCZT");
                processImage(outputDirectory);
            }
        }
        roiManager.close();
    }

    private void processImage(String outputDirectory){
        ImagePlus imp = WindowManager.getCurrentImage();
        String filename = imp.getShortTitle();

        IJ.run(imp, "Split Channels", "");
        String[] channelTitles = WindowManager.getImageTitles();
        for (int i = 0; i < channelTitles.length - 1; i++) {
            WindowManager.getImage(channelTitles[i]).close();
        }
        imp = WindowManager.getCurrentImage();
        imp = ZProjector.run(imp, "max");
        IJ.run(imp, "Enhance Contrast", "saturated=0.35");
        imp.setTitle("DAPI Z-project");

        //sets what will be visible in the resulting cropped image - currently should be DAPI
        ImagePlus originalImage = imp.duplicate();
        IJ.run(originalImage, "Blue", "");
        originalImage.setTitle("Original Z-Project");
        originalImage.hide();

        imp.hide();

        Roi[] mitotic = findMitotic(imp.duplicate());
        Roi[] cells = thresholdCells(imp.duplicate());

        ArrayList<Roi> mitoticCells = thresholdMitotic(mitotic, cells);
        saveImages(mitoticCells, originalImage, filename, outputDirectory);
        IJ.run("Close All", "");
    }


    private Roi[] findMitotic(ImagePlus ZStack){
        ImagePlus imp = ZStack;
        imp.setTitle("Mitotic Z-project");
        imp.show();
        ImageConverter ic = new ImageConverter(imp);
        ic.convertToGray8();
        imp.updateAndDraw();
        IJ.run(imp, "Subtract Background...", "rolling=50");
        IJ.setAutoThreshold(imp, "MaxEntropy dark");
        IJ.run(imp, "Analyze Particles...", "size=150-Infinity pixel show=Masks clear add");
        ImagePlus masks = WindowManager.getCurrentImage();
        Roi[] mitotic = roiManager.getRoisAsArray();
        masks.close();
        roiManager.runCommand(imp,"Show None");
        return mitotic;
    }

    private Roi[] thresholdCells(ImagePlus ZStack) {
        ImagePlus imp = ZStack;
        imp.setTitle("Cell Z-project");
        imp.show();
        roiManager.runCommand("Show None");
        ImageConverter ic = new ImageConverter(imp);
        ic.convertToGray16();
        imp.updateAndDraw();
        IJ.run(imp, "Gaussian Blur...", "sigma=1.5");
        IJ.setAutoThreshold(imp, "Huang dark");
        ByteProcessor bp = imp.createThresholdMask();
        imp = new ImagePlus("title", bp);
        IJ.run(imp, "Watershed", "");
        IJ.run(imp, "Analyze Particles...", "size=200-Infinity pixel show=Masks clear add");
        ImagePlus masks = WindowManager.getCurrentImage();
        Roi[] cells = roiManager.getRoisAsArray();
        masks.close();
        ZStack.show();
        return cells;
    }

    private ArrayList<Roi> thresholdMitotic(Roi[] mitotic, Roi[] cells) {
        ArrayList<Roi> mitoticCells = new ArrayList<>();
        System.out.println(mitotic.length);
        for (Roi mitoticCell : mitotic) {
            double[] centre = mitoticCell.getContourCentroid();
            for (Roi cell : cells) {
                if (cell.containsPoint(centre[0], centre[1]) && (!mitoticCells.contains(cell))) {
                    mitoticCells.add(cell);
                }
            }
        }
        return mitoticCells;
    }

    private void saveImages(ArrayList<Roi> mitoticCells, ImagePlus original, String filename, String outputDirectory) {
        //create a new folder to save files in
        original.show();
        WindowManager.getWindow("Original Z-Project");
        String newDirectory = outputDirectory + filename;
        String filePath = Paths.get(outputDirectory, filename).toString();
        new File(newDirectory).mkdir();

        roiManager.reset();
        for (Roi roi : mitoticCells) {
            roiManager.addRoi(roi);
        }
        roiManager.runCommand("Show All");
        for (int i=0; i < mitoticCells.size(); i++) {
            ImagePlus impCrop = cropRoi(original, i);
            //IJ.saveAs(impCrop, "Tiff", newDirectory + filename + "_" + i);
            IJ.saveAs(impCrop, "Tiff", Paths.get(filePath, filename + "_" + i).toString());
            //IJ.saveAs("Tiff", Paths.get(filePath, filename).toString());
            impCrop.close();
        }
    }

    private ImagePlus cropRoi(ImagePlus original, int no) {
        //for 100x images pixel width of 0.1095202 microns
        double pixelToMicron = 9.130736;

        double[] centrePoint = roiManager.getRoi(no).getContourCentroid();
        Point pt = new Point((int) centrePoint[0], (int) centrePoint[1]);
        double lCrop = 30*pixelToMicron;
        original.setRoi (new Roi(pt.x - lCrop / 2, pt.y - lCrop / 2, lCrop, lCrop));
        return original.crop();
    }
}
