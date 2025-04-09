# HKUMX-PatchSeg
An example GitHub repository for patch-level segmentation of pathology slides.

## Pipeline

This pipeline generally follows the steps illustrated in  https://github.com/andreped/NoCodeSeg

1. **Export Patches and Annotations Using QuPath**:

   1. Run scripts in QuPath's 'Automate' feature to split the slides and their corresponding annotations into patches and masks. An example script is provided: [ExportImageAndMask](./scripts/ExportImageAndMask.groovy).  Please note that necessary modifications may be required. This step must be performed on a server with a GUI interface for QuPath.

   2. Example File arthitecture (/Deep Learning/Chondroid/QMH/Histology/Annotation_New/tiles_512_10x_FiveClass_IdentifyTumor):

      ```
      tiles_512_10x_FiveClass_IdentifyTumor/
      	├── Images  # patches/Images
      		├── CHS001-WSI01  # WSI ID (Cse.ID-WSI.ID)
      			├── CHS001-WSI01 [d=4,x=10240,y=59392,w=2048,h=2048].jpeg # Image file
      			└──...
      		├── ...
      		└──	CHS078-WSI01
      	└── Labels  # Annotations/Masks
      		├── CHS001-WSI01
            ├── CHS001-WSI01 [d=4,x=10240,y=59392,w=2048,h=2048].png # mask file
      			└──...
      		├── ...
      		└──	CHS078-WSI01
      ```

   3. Upload the patches and masks onto the GPU server. Split the dataset into training and testing set (idealy with respect to  patietns, i.e., WSIs of patients in the training set will not appear in the testing one). 

2. Patch-level training and inference (Example repo: https://github.com/peterlipan/patch_seg.)

3. Import the predicted masks onto WSI using QuPath.
