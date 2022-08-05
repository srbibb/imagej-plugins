import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Picks out the mitotic cells from an image containing mitotic and interphase cells.
// Saves each identified cell to a chosen output folder as an individual cropped version
// of the original image
// Takes either a folder of Z-Stack images or a folder of Z-Projected images as input
// Images must be in either .tif or .ims format

public class Sarah_Mitotic_Threshold implements PlugIn {

    RoiManager roiManager;

    public void run(String args) {
        String mode = modeSelect();

        // Lets user choose the input and output directory
        DirectoryChooser dc = new DirectoryChooser("Bio-Formats Mass Importer");
        String inputDirectory = dc.getDirectory();
        dc = new DirectoryChooser("Output Folder");
        String outputDirectory = dc.getDirectory();

        roiManager = new RoiManager();

        // List of files to actually open with Bio-Formats Importer
        ArrayList<String> filesToOpen = new ArrayList<>();
        File dir = new File(inputDirectory);
        File[] files = dir.listFiles();

        // Only opens files of the correct format in the folder
        for (int m = 0; m < files.length; m++) {
            filesToOpen.add(files[m].getPath());
            String id = filesToOpen.get(m);
            if (((id.contains(".ims")) || (id.contains(".tif"))) && !(id.contains(".xml"))) {
                roiManager.reset();
                IJ.run("Bio-Formats Importer", "open=" + id + " color_mode=Default open_files view=Hyperstack stack_order=XYCZT");
                processImage(outputDirectory, mode);
            }
        }
        roiManager.close();
    }

    // Selects the right channel in the image and Z-projects if necessary
    private void processImage(String outputDirectory, String mode){
        ImagePlus imp = WindowManager.getCurrentImage();
        String filename = imp.getShortTitle();

        // Separates the DAPI channel and closes unneeded channels
        IJ.run(imp, "Split Channels", "");
        String[] channelTitles = WindowManager.getImageTitles();
        for (int i = 0; i < channelTitles.length - 1; i++) {
            WindowManager.getImage(channelTitles[i]).close();
        }
        imp = WindowManager.getCurrentImage();

        // Z-projects images if necessary
        if (mode == "Folder of Z-Stacks") {
            imp = ZProjector.run(imp, "max");
        }
        IJ.run(imp, "Enhance Contrast", "saturated=0.35");
        imp.setTitle("DAPI Z-project");

        // Sets what will be visible in the resulting cropped image - currently is only DAPI
        // Changes image colour to blue
        ImagePlus originalImage = imp.duplicate();
        IJ.run(originalImage, "Blue", "");
        originalImage.setTitle("Original Z-Project");
        originalImage.hide();
        imp.hide();

        Roi[] mitotic = findMitotic(imp.duplicate());
        Roi[] cells = thresholdCells(imp.duplicate());

        // Finds and saves the identified mitotic cells, then closes all images
        ArrayList<Roi> mitoticCells = thresholdMitotic(mitotic, cells);
        saveImages(mitoticCells, originalImage, filename, outputDirectory);
        IJ.run("Close All", "");
    }


    // Finds the areas of the image thought to be mitotic using thresholding and returns
    // them as an array of ROIs
    private Roi[] findMitotic(ImagePlus ZStack){
        ImagePlus imp = ZStack;
        imp.setTitle("Mitotic Z-project");
        imp.show();

        // Applies preprocessing to make thresholding more accurate
        ImageConverter ic = new ImageConverter(imp);
        ic.convertToGray8();
        imp.updateAndDraw();
        IJ.run(imp, "Subtract Background...", "rolling=50");
        IJ.setAutoThreshold(imp, "MaxEntropy dark");

        // Only areas larger than 150 pixels are saved to avoid noise highlighting incorrect cells
        IJ.run(imp, "Analyze Particles...", "size=150-Infinity pixel show=Masks exclude clear add");
        ImagePlus masks = WindowManager.getCurrentImage();

        Roi[] mitotic = roiManager.getRoisAsArray();
        masks.close();
        roiManager.runCommand(imp,"Show None");
        return mitotic;
    }

    // Thresholds every separate cell in the image and returns them as an array of ROIs
    private Roi[] thresholdCells(ImagePlus ZStack) {
        ImagePlus imp = ZStack;
        imp.setTitle("Cell Z-project");
        imp.show();
        roiManager.runCommand("Show None");

        // Applies preprocessing to make thresholding more accurate
        ImageConverter ic = new ImageConverter(imp);
        ic.convertToGray16();
        imp.updateAndDraw();
        IJ.run(imp, "Gaussian Blur...", "sigma=1.5");
        IJ.setAutoThreshold(imp, "Huang dark");
        ByteProcessor bp = imp.createThresholdMask();
        imp = new ImagePlus("title", bp);

        // Separates cells which are close together and are otherwise classed as a single cell
        IJ.run(imp, "Watershed", "");

        // Only areas larger than 200 pixels are saved to avoid noise being counted as a cell
        IJ.run(imp, "Analyze Particles...", "size=200-Infinity pixel show=Masks exclude clear add");
        ImagePlus masks = WindowManager.getCurrentImage();

        Roi[] cells = roiManager.getRoisAsArray();
        masks.close();
        ZStack.show();
        return cells;
    }

    // Finds the cells which are mitotic by collecting any located cells which contain
    // an area identified as mitotic. Returns these cells as an array of ROIs
    private ArrayList<Roi> thresholdMitotic(Roi[] mitotic, Roi[] cells) {
        ArrayList<Roi> mitoticCells = new ArrayList<>();
        for (Roi mitoticCell : mitotic) {
            double[] centre = mitoticCell.getContourCentroid();
            for (Roi cell : cells) {
                // If a cell contains the centre of a mitotic ROI it is selected as a mitotic cell
                // Only adds a cell if it has not already been identified
                if (cell.containsPoint(centre[0], centre[1]) && (!mitoticCells.contains(cell))) {
                    mitoticCells.add(cell);
                }
            }
        }
        return mitoticCells;
    }

    // Iterates through each mitotic cell and saves the cropped image to the output directory
    private void saveImages(ArrayList<Roi> mitoticCells, ImagePlus original, String filename, String outputDirectory) {
        original.show();
        WindowManager.getWindow("Original Z-Project");
        String filePath = Paths.get(outputDirectory).toString();

        roiManager.reset();
        for (Roi roi : mitoticCells) {
            roiManager.addRoi(roi);
        }
        roiManager.runCommand("Show All");

        // For each cell, takes the Z-project of the original image and saves the cropped version
        for (int i=0; i < mitoticCells.size(); i++) {
            ImagePlus impCrop = cropRoi(original, i);
            Path path = Paths.get(filePath, filename + "_" + i);
            IJ.saveAs(impCrop, "Tiff", path.toString());
            // TEMP: save as png to view images easily
            IJ.saveAs(impCrop, "PNG", path.toString());
            impCrop.close();
        }
    }

    // Crops the original image to an area around the mitotic cell
    // Each image is centred on the cell and is 30 microns by 30 microns
    // Returns an ImagePlus of the cropped image
    private ImagePlus cropRoi(ImagePlus original, int no) {
        //for 100x images pixel width of 0.1095202 microns
        double pixelToMicron = 9.130736;
        double[] centrePoint = roiManager.getRoi(no).getContourCentroid();
        Point pt = new Point((int) centrePoint[0], (int) centrePoint[1]);

        // For 100x images, 30 microns comfortably shows cells without too much unneeded space
        double lCrop = 30*pixelToMicron;

        // Creates a ROI which is 15 microns in each direction around the centre of the current cell
        original.setRoi (new Roi(pt.x - lCrop / 2, pt.y - lCrop / 2, lCrop, lCrop));
        return original.crop();
    }

    // Allows the user to select the required input using a dialog box
    // Returns a string containing the chosen mode
    private String modeSelect() {
        GenericDialog channelDialog = new NonBlockingGenericDialog("Mode Select");
        String[] modeArray = new String[]{"Z-Projected folder", "Folder of Z-Stacks"};
        channelDialog.addChoice("Form of input:", modeArray, "Z-Projected folder");
        channelDialog.showDialog();
        return channelDialog.getNextChoice();
    }
}
