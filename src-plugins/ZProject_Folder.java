import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ZProject_Folder implements PlugIn {

    public void run(String arg) {

        //Choose directory
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
                IJ.run("Bio-Formats Importer", "open=" + id + " color_mode=Default open_files view=Hyperstack stack_order=XYCZT");
                ImagePlus imp = WindowManager.getCurrentImage();
                ImagePlus original = WindowManager.getCurrentImage();
                String filename = imp.getShortTitle();
                imp = ZProjector.run(imp, "max");
                String filePath = Paths.get(outputDirectory, filename).toString();
                IJ.saveAs(imp, "Tiff", filePath);
                //temp: save as jpeg to confirm images easily
                IJ.saveAs(imp, "JPEG", filePath);
                imp.close();
                original.close();
            }
        }
    }

}
