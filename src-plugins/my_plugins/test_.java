package my_plugins;

import ij.IJ;
import ij.WindowManager;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.plugin.ZProjector;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;

public class test_ implements PlugIn {
    public void run(String s) {
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
            IJ.run("Bio-Formats Importer", "open=" + id + " color_mode=Default open_files view=Hyperstack stack_order=XYCZT");
            ImagePlus imp = WindowManager.getCurrentImage();
            ImagePlus original = WindowManager.getCurrentImage();
            String filename = imp.getShortTitle();
            String[] split = filename.split("-");
            filename = split[1];
            String filePath = Paths.get(outputDirectory, filename).toString();
            if (id.contains(".tif")) {
                IJ.saveAs(imp, "Tiff", filePath);
            } else {
                IJ.saveAs(imp, "JPEG", filePath);
            }
            imp.close();
            original.close();
        }
    }
}
