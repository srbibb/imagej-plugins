import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.DirectoryChooser;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ZProject_Folder implements PlugIn {

    public void run(String arg) {

        //Choose directory
        DirectoryChooser dc = new DirectoryChooser("Bio-Formats Mass Importer");
        String baseDirectory = dc.getDirectory();

        // list of files to actually open with Bio-Formats Importer
        ArrayList<String> filesToOpen = new ArrayList<>();

        // process all files in the chosen directory
        File dir = new File(baseDirectory);
        File[] files = dir.listFiles();
        boolean isFirst = true;
        int[] choicesOrder = new int[7];

        for (int m = 0; m < files.length; m++) {
            filesToOpen.add(files[m].getPath());
            String id = filesToOpen.get(m);
            if (id.contains(".tif")) {
                IJ.run("Bio-Formats Importer", "open=" + id + " color_mode=Default open_files view=Hyperstack stack_order=XYCZT");

                int numwin = WindowManager.getWindowCount();
                int[] Idlist = WindowManager.getIDList();

                String[] choicesArray = new String[7];
                // String baseDirectory = IJ.getDirectory("current");

                for (int x = 0; x < numwin; x++) {
                    IJ.selectWindow(Idlist[x]);

                    //create a new folder to save files in
                    ImagePlus imp = WindowManager.getCurrentImage();
                    String filename = imp.getShortTitle();
                    String newDirectory = baseDirectory + filename;
                    String filePath = Paths.get(baseDirectory, filename).toString();
                    new File(newDirectory).mkdir();

                    //split channels
                    new ChannelSplitter();
                    ImagePlus[] channels = ChannelSplitter.split(imp);

                    //Z project each channels
                    ImagePlus zProject[] = new ImagePlus[channels.length];
                    List<String> channelNameList = new ArrayList<>();
                    for (int i = 0; i < channels.length; i++) {
                        IJ.run(channels[i], "Z Project...", "projection=[Average Intensity]");
                        zProject[i] = WindowManager.getCurrentImage();
                        IJ.run(zProject[i], "Enhance Contrast", "saturated=0.35");
                        IJ.saveAs("Tiff", Paths.get(filePath, zProject[i].getShortTitle()).toString());
                        channelNameList.add(zProject[i].getTitle());

                    }

                    //Merge Channels
                    choicesArray[0] = channelNameList.get(0);
                    choicesArray[1] = channelNameList.get(1);
                    choicesArray[2] = channelNameList.get(2);
                    choicesArray[3] = "--";
                    choicesArray[4] = "--";
                    choicesArray[5] = "--";
                    choicesArray[6] = "--";
                    IJ.run(imp, "Merge Channels...", mergeChannels(choicesArray));
                    ImagePlus merge = WindowManager.getCurrentImage();
                    IJ.run(merge, "RGB Color","");
                    imp.close();

                    //Save and close
                    IJ.saveAs("Tiff", Paths.get(filePath, filename + "_Composite").toString());
                    IJ.run("Close All", "");

                }
            }
        }
    }

    //Take a 7 value String array containing filenames (created in channelSelector) and returns a String to be used to
    // merge channels

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
