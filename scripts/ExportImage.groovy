/**
 * Script to export annotations as labeled tiles for QuPath > 0.2*.
 *
 * All patches will be exported to the same directory called 'tiles' inside the Project directory
 * The patches will be filtered based on tissue content, and finally moved to respective the
 * subdirectories: Images and Labels within the 'tiles' folder
 *
 * Each patch's filename contains the original WSI ID, and images are saved as PNG (by default)
 * and ground truth as TIF
 *
 * The downsampling level can be set by the user, default value is 4.
 *
 * Code is inspired by the script from the QuPath documentations, written by Pete Bankhead:
 * https://qupath.readthedocs.io/en/stable/docs/advanced/exporting_images.html#tile-exporter
 *
 * @author André Pedersen
 */


import qupath.lib.images.servers.LabeledImageServer
import java.awt.image.Raster
import javax.imageio.ImageIO;


// ----- SET THESE PARAMETERS -----
double downsample = 4  // which downsampling level to use -> how much to downsample the patches
double glassThreshold = 50  // which threshold to use for tissue detection (lower value => more tissue included in mask)
double percentageThreshold = 0.01  // if a patch contains less than X amount of tissue, it should be neglected (range [0.0, 1.0])
int patchSize = 512  // generated patch size
int pixelOverlap = 0  // stride for which patches are generated
//def imageExtension = ".tif"
def imageExtension = ".jpeg"
int nb_channels = 3;
def multiChannel = false;
// --------------------------------


def imageData = getCurrentImageData()

// Define output path (relative to project)
def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())
def pathOutput = buildFilePath(PROJECT_BASE_DIR, 'tiles_testing_set_512_x10_thre_01')
mkdirs(pathOutput)

// Create an exporter that requests corresponding tiles from the original & labeled image servers
new TileExporter(imageData)
    .downsample(downsample)          // Define export resolution
    .imageExtension(imageExtension)  // Define file extension for original pixels (often .tif, .jpg, '.png' or '.ome.tif')
    .tileSize(patchSize)             // Define size of each tile, in pixels
    .annotatedTilesOnly(false)        // If true, only export tiles if there is a (labeled) annotation present
    .overlap(pixelOverlap)           // Define overlap, in pixel units at the export resolution
    .writeTiles(pathOutput)          // Write tiles to the specified directory


// attempt to delete unwanted patches, both some formats as well as patches containing mostly glass
// Iterate through all your tiles
File folder = new File(pathOutput)
File[] listOfFiles = folder.listFiles()

// for each patch
listOfFiles.each { tile ->
    // skip directories within masks folder, and skip all ground truth patches
    if (tile.isDirectory())
        return;
    def currPath = tile.getPath()
    if (!currPath.endsWith(imageExtension))
        return;
    
    // load TIFF images back again, estimate patch glass density, and remove patches with lots
    // of glass based on user-defined threshold
    def image = ImageIO.read(new File(currPath))
    Raster raster = image.getRaster();
    
    // estimate amount of tissue in patch
    def tissue = 0;
    for (int y = 0; y < image.getHeight(); ++y) {
        for (int x = 0; x < image.getWidth(); ++x) {
            double currDist = 0
            for (int z = 0; z < nb_channels; ++z) {
                currDist += raster.getSample(x, y, z)
            }
            currDist = ((currDist / 3) > (255 - glassThreshold)) ? 0 : 1;
            if (currDist > 0) {
                ++tissue
            }
        }
    }
    
    // remove patches containing less tissue, dependent on user-defined threshold, and move accepted patches to respective folders
    def amountTissue = tissue / (image.getWidth() * image.getHeight());
    
    def dir_subimage = new File(pathOutput + "/" + tile.getName().split( )[0]);
    if (!dir_subimage.isDirectory())
        dir_subimage.mkdir()
        
    if (amountTissue < percentageThreshold) {
        tile.delete()
    } else {
        tile.renameTo(pathOutput +  "/" + tile.getName().split( )[0]+ "/" + tile.getName())
    }
}

print "Done!"

// reclaim memory - relevant for running this within a RunForProject
Thread.sleep(100);
javafx.application.Platform.runLater {
    getCurrentViewer().getImageRegionStore().cache.clear();
    System.gc();
}
Thread.sleep(100);