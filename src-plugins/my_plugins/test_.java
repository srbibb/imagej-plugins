package my_plugins;

import ij.IJ;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.ImagePlus;

public class test_ implements PlugIn {
    public void run(String s) {
        IJ.run("Bio-Formats Importer");
        ImagePlus imp = WindowManager.getCurrentImage();
        IJ.run(imp, "Split Channels", "");
        String[] channelTitles = WindowManager.getImageTitles();
        WindowManager.getImage(channelTitles[0]).close();
        WindowManager.getImage(channelTitles[1]).close();
        WindowManager.getImage(channelTitles[2]).close();
    }
}
