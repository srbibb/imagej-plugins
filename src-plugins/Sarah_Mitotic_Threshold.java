import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
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
        IJ.run("Bio-Formats Importer");// Open new file
        roiManager = new RoiManager();
        ImagePlus imp = WindowManager.getCurrentImage();
        IJ.run(imp, "Split Channels", "");
        String[] channelTitles = WindowManager.getImageTitles();
        for (int i = 0; i < channelTitles.length-1; i++) {
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

        Roi[] mitotic = findMitotic(roiManager, imp.duplicate());
        Roi[] cells = thresholdCells(imp.duplicate());

        ArrayList<Roi> mitoticCells = thresholdMitotic(mitotic,cells);
        saveImages(mitoticCells, originalImage);
    }

    private Roi[] findMitotic(RoiManager roiManager, ImagePlus ZStack){
        ImagePlus imp = ZStack;
        imp.setTitle("Mitotic Z-project");
        imp.show();
        ImageConverter ic = new ImageConverter(imp);
        ic.convertToGray8();
        imp.updateAndDraw();
        IJ.run(imp, "Subtract Background...", "rolling=50");
        IJ.setAutoThreshold(imp, "MaxEntropy dark");
        IJ.run(imp, "Analyze Particles...", "size=200-Infinity pixel show=Masks display clear summarize add");
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
        IJ.run(imp,"Threshold...","");
        ByteProcessor bp = imp.createThresholdMask();
        imp = new ImagePlus("title", bp);
        IJ.run(imp, "Watershed", "");
        IJ.run(imp, "Analyze Particles...", "size=200-Infinity pixel show=Masks display clear summarize add");
        ImagePlus masks = WindowManager.getCurrentImage();
        Roi[] cells = roiManager.getRoisAsArray();
        masks.close();
        ZStack.show();
        return cells;
    }

    private ArrayList<Roi> thresholdMitotic(Roi[] mitotic, Roi[] cells) {
        ArrayList<Roi> mitoticCells = new ArrayList<>();
        for (Roi mitoticCell : mitotic) {
            double[] centre = mitoticCell.getContourCentroid();
            for (Roi cell : cells) {
                if (cell.containsPoint(centre[0], centre[1])) {
                    mitoticCells.add(cell);
                }
            }
        }
        return mitoticCells;
    }

    private void saveImages(ArrayList<Roi> mitoticCells, ImagePlus original) {
        //create a new folder to save files in
        original.show();
        WindowManager.getWindow("Original Z-Project");
        //String filename = original.getShortTitle();
        String newDirectory = "D:/icm/data/cropped";
        new File(newDirectory).mkdir();

        //for 100x images pixel width of 0.1095202 microns
        double pixelToMicron = 9.130736;

        roiManager.reset();
        for (Roi roi : mitoticCells) {
            roiManager.addRoi(roi);
        }
        roiManager.runCommand("Show All");
        for (int i=0; i < mitoticCells.size(); i++) {
            double[] centrePoint = roiManager.getRoi(i).getContourCentroid();
            Point pt = new Point((int) centrePoint[0], (int) centrePoint[1]);
            double lCrop = 25*pixelToMicron;
            original.setRoi (new Roi(pt.x - lCrop / 2, pt.y - lCrop / 2, lCrop, lCrop));
            ImagePlus impCrop = original.crop();
            IJ.saveAsTiff(impCrop, Paths.get(newDirectory, "Cropped" + i).toString());
            impCrop.close();
        }
    }

    private String mergeChannels(String[] choicesArray) {
        String mergeString = "";
        for (int x = 0; x < 7; x++) {
            if (!choicesArray[x].equals("--")) {
                String channelString = "c" + (x + 1) + "=[" + choicesArray[x] + "] ";
                mergeString = mergeString.concat(channelString);
            }
        }
        mergeString = mergeString.concat("create keep");
        return mergeString;
    }

}
