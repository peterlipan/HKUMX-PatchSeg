import ij.plugin.filter.ThresholdToSelection
import ij.process.ImageProcessor
import qupath.lib.objects.PathObjects
import qupath.lib.regions.ImagePlane
import qupath.imagej.processing.RoiLabeling
import ij.IJ
import static qupath.lib.gui.scripting.QPEx.*

// --- SET THESE PARAMETERS ---
def classNames = ["Bone", "Marrow", "Normal cartilage", "Soft tissue", "Tumor"]; // List of classes
def rootOutput = "Z:/Histology/Segmentation_Visualisation/Predictions_IdentifyTumor_x10_512_thre_80_b1"; // Base path for all classes
def patchFormat = "tif";  // Set to "png" or "tif"
// ----------------------------

// Get the main QuPath data structures
def imageData = getCurrentImageData();
def hierarchy = imageData.getHierarchy();
def server = imageData.getServer();
def plane = getCurrentViewer().getImagePlane();

def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName());

// Iterate through each class
classNames.each { className ->
    def dirOutput = new File("${rootOutput}/${className}/${name}"); // Directory for the current class and slide
    print dirOutput;
    
    if (!dirOutput.isDirectory()) {
        print "${dirOutput} is not a valid directory!";
        return;
    }

    print "Clearing existing annotations for class: ${className}(Predicted)...";
    selectObjects {
        return it.isAnnotation() && it.getPathClass() == getPathClass("${className}(Predicted)");
    }
    clearSelectedObjects();

    print "Existing annotations cleared for class: ${className}(Predicted). Continuing...";


    def files = dirOutput.listFiles({ f -> f.isFile() && f.getName().endsWith("]." + patchFormat) } as FileFilter) as List;
    if (files.isEmpty()) {
        print 'No mask files found in ' + dirOutput;
        return;
    }

    // Loading bar
    int spaces = 40;
    float progress = 100.0;
    int counter = 0;
    int nbPatches = files.size();

    // Create annotations for all the files in the current class
    def annotations = [];
    files.each { file ->
        String hash = "#" * Math.ceil((counter * spaces) / nbPatches);
        println String.format("[%-" + spaces + "s] %d%s%d\r", hash, counter, '/', nbPatches);
        counter++;
        
        def currentName = GeneralTools.getNameWithoutExtension(getProjectEntry().getImageName());
        def filename = file.getName();
        if (!filename.contains(currentName)) {
            print "Skipping file: ${filename} as it does not match current image name.";
            return;
        }

        try {
            def annotation = parseAnnotation(file, plane, className);
            if (annotation != null) {
                annotations << annotation;
            }
        } catch (Exception e) {
            print 'Unable to parse annotation from ' + file.getName() + ': ' + e.getLocalizedMessage();
        }
    }

    resolveHierarchy();

    // Replace classification for the current class
    replaceClassification(null, "${className}(Predicted)");

    // Merge annotations for the current class
    if (!annotations.isEmpty()) {
        print "Merging annotations for class: ${className} (might take some time...)";
        
        // Select annotations to merge
        selectObjects {
            return it.isAnnotation() && it.getPathClass() == getPathClass("${className}(Predicted)");
        }
        mergeSelectedAnnotations();
    } else {
        print "No annotations to merge for class: ${className}";
    }
}

print "Done!";
// reclaim memory - relevant for running this within a RunForProject
Thread.sleep(100);
javafx.application.Platform.runLater {
    getCurrentViewer().getImageRegionStore().cache.clear();
    System.gc();
}
Thread.sleep(100);

// Function to parse annotations
def parseAnnotation(File file, ImagePlane plane, String className) {
    def filename = file.getName();
    def imp = IJ.openImage(file.getPath());

    if (imp == null) {
        print "Failed to open image: ${file.getPath()}";
        return null;
    }

    def parts = filename.split(' ');
    def regionParts = parts[-1].split(",") as List;

    // Handle scenario where there was not done any downsampling (d=1 skipped!)
    if (regionParts.size() == 4) {
        regionParts[0] = regionParts[0][1..-1]
        regionParts.add(0, "[d=1");
    }

    def downsample = regionParts[0].replace("[d=", "") as float;
    int x = regionParts[1].replace("x=", "") as int;
    int y = regionParts[2].replace("y=", "") as int;

    // Create the ROI
    def bp = imp.getProcessor();
    int n = bp.getStatistics().max as int;
    def rois = RoiLabeling.labelsToConnectedROIs(bp, n);

    def pathObjects = rois.collect { roi ->
        if (roi == null) return null;
        def roiQ = IJTools.convertToROI(roi, -x/downsample, -y/downsample, downsample, plane);
        return PathObjects.createAnnotationObject(roiQ);
    }.findAll { it != null }; // Filter out nulls

    if (!pathObjects.isEmpty()) {
        addObjects(pathObjects);
        return pathObjects; // Return the created objects
    } else {
        print "No valid ROIs created from ${filename}.";
        return null;
    }
}
