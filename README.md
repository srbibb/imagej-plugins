# imagej-plugins
ImageJ plugins and setup. 

Sarah_Mitotic_Threshold: Identifies and crops images of 
mitotic cells in an image. The plugin processes a folder 
of images in either .ims or .tif format, and saves each 
cell as a cropped image in a chosen output folder. 
Can take either a folder of Z-stacks or a folder of 
Z-projected images as input. 

Currently, saves only the DAPI channel in the cropped image, but can also
save a composite image. Assumes no more than 52 mitotic cells in an image.

ZProject_Folder: Z-projects each image in a folder of 
.ims images, and saves each Z-projected image to a 
chosen output folder.