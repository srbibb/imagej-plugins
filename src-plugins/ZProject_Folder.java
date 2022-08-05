import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ZProject_Folder implements PlugIn {

    public void run(String arg) {

        //Choose directory
        DirectoryChooser dc = new DirectoryChooser("Bio-Formats Mass Importer");
        String baseDirectory = dc.getDirectory();
        dc = new DirectoryChooser("Output Folder");
        String outputDirectory = dc.getDirectory();

        // List of files to actually open with Bio-Formats Importer
        ArrayList<String> filesToOpen = new ArrayList<>();
        File dir = new File(baseDirectory);
        File[] files = dir.listFiles();

        // For each file in the directory, creates a Z-project image using maximum intensity
        // and saves it to the output folder
        for (int m = 0; m < files.length; m++) {
            filesToOpen.add(files[m].getPath());
            String id = filesToOpen.get(m);
            if ((id.contains(".ims")) && !(id.contains("xml"))) {
                IJ.run("Bio-Formats Importer", "open=" + id + " color_mode=Default open_files view=Hyperstack stack_order=XYCZT");
                ImagePlus imp = WindowManager.getCurrentImage();
                ImagePlus original = WindowManager.getCurrentImage();
                String filename = imp.getShortTitle();
                imp = ZProjector.run(imp, "max");
                String filePath = Paths.get(outputDirectory, filename).toString();
                IJ.saveAs(imp, "Tiff", filePath);
                // TEMP: save as png to confirm images easily
                IJ.saveAs(imp, "PNG", filePath);
                imp.close();
                original.close();
            }
        }
    }

}
