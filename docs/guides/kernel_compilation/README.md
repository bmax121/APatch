# How to compile the kernel for unsupported devices like Samsung..?
<h3><p><hr>
<b>What you'll need :</b> A Working brain 🧠, PC/RDP with any linux GUI distro, Knowledge of basic commands in Linux.
</p>
	
### Requirements for compiling kernels : (Paste this in terminal.)
 ```
sudo apt update -y
sudo apt install default-jdk git-core gnupg flex bison gperf build-essential zip curl libc6-dev libncurses5-dev x11proto-core-dev libx11-dev libreadline6-dev libgl1-mesa-glx libgl1-mesa-dev python3 make sudo gcc g++ bc grep tofrodos python3-markdown libxml2-utils xsltproc zlib1g-dev libncurses5 python-is-python3 libc6-dev libtinfo5 ncurses-dev make -y
```
<br>❗The video Guide for this tutorial can be found here : Open in YouTube </h3><hr>

### 01. Download the kernel source from the [Samsung Opensource]( https://opensource.samsung.com/main).
<img src="https://github.com/ravindu644/APatch/assets/126038496/aad04d45-e1b3-4baf-a8e0-2ef27d7dae55" width="45%">

### 02. Extract the ```Kernel.tar.gz``` from the source zip, unarchive it using this command.
```
tar xvf Kernel.tar.gz; rm Kernel.tar.gz
```
#### Additional note : If your Kernel source's folders are locked like this, you can change the entire folder's permissions to read and write.
- Problem : <br><img src="https://github.com/ravindu644/APatch/assets/126038496/11565943-f329-4782-b7e9-0f0d0b8ee2fd" width="55%">
- Solution : <br><img src="https://github.com/ravindu644/APatch/assets/126038496/8d975f38-ea65-458c-b0f3-0544c3b4303b" width="45%">


### 03. Open build_kernel.sh and download the compilers from the internet. (Google search its name).
<img src="https://github.com/ravindu644/APatch/assets/126038496/26daf156-b6d1-4082-96f6-f958416946eb" width="70%"><br>
- Both Clang and GCC is required.
### 04. Edit the build_kernel.sh to add compilers' path like this:
```
BUILD_CROSS_COMPILE=/path/to/gcc/aarch64-linux-android-
KERNEL_LLVM_BIN=/path/to/compiler/clang
```
### 05. Edit the Makefile.
- if you found these variables : ```CROSS_COMPILE```, ```REAL_CC``` or ```CC```, ```CFP_CC``` in your make file with some paths, you have to edit their paths too, like we did in above step.
### 06. Edit the build script.
- Exporting the Android version and architecture (Add these lines below the ```#!/bin/bash```) :
  ```
  export ARCH=arm64
  export PLATFORM_VERSION=13
  export ANDROID_MAJOR_VERSION=t
  ```
- Adding python2 to path : (Create the local > bin folders in your home dir first)
  ```
  ln -s /usr/bin/python2.7 $HOME/local/bin/python
  export PATH=$HOME/local/bin:$PATH
  ```
- Cleaning the source before compiling :
  ```
  make YOUR_ARGS clean && make YOUR_ARGS mrpropr
  ```
- Editing the menuconfig after making the defconfig :
  ```
  make YOUR_ARGS XXXX_defconfig
  make YOUR_ARGS menuconfig
  ```
### Our build script must looks like this, after making the changes: (This is an example.)
  <img src="https://github.com/ravindu644/APatch/assets/126038496/e75ca37e-e038-425f-8040-1ce521796a58" width="80%">
  
### 06. Use this commit to fix "symbol versioning failure for gsi_write_channel_scratch" error. (it's an universal error for all the snapdragon kernel sources)
- https://github.com/ravindu644/android_kernel_samsung_sm_a525f/commit/0cc860c380b3b35a5cd4db039b8c3fd03db7c771

## Now we finished setting up the basic configurations for kernel compilation.

### 07. Rename your ```build_kernel.sh``` to ```build.sh```.
- Then, grant the executable permissions to it using this command.
  ```
  chmod +x build.sh
  ```
### 08. Now, run the build script using this command :
  ```
./build.sh
```
## After a couple of seconds, the "menuconfig" should appear.
- Additional notes : Press space bar to enable/disable or enable as a module <M>.
<hr>

# How to disable kernel securities + Enable the required features from menuconfig..?
### 01. Open ```→ General setup → Local version - append to kernel release``` => Choose any string you like.
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/448a24b9-454b-47b9-82a8-0b9c2804e693">

### 02. ```→ General setup → Configure standard kernel features (expert users)``` => Enable everything except "```sgetmask/ssetmask syscalls support``` and ```Sysctl syscall support```"
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/8927d898-d3ef-471a-8f68-bbe418068565" width="75%">

### 03. ```→ Enable loadable module support``` => Enable "```Forced module loading```", "```Module unloading```", "```Forced module unloading```" and "```Module versioning support```" ; Also Disable "```Module signature verification```"
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/ad4b7edd-4978-46f4-b84f-396e5e9b8999" width="75%">

### 04. ```→ Kernel Features``` => Disable "```Enable RKP (Realtime Kernel Protection) UH feature```", "```Enable LKM authentication by micro hypervisor```", "```Block LKM by micro hypervisor```", "```Enable micro hypervisor feature of Samsung```" respectively.
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/d821da9f-0b45-4701-b681-3996bec509be" width="75%">

#### Additional notes : If you can't find them in the "```→ Kernel Features```", they are in "```→ Boot options```". In samsung S/N 10 series, there's a thing called "JOPP Prevention", disable these things too.
### 05. ```→ Boot options``` => enable "```Build a concatenated Image.gz/dtb by default```" and "```Kernel compression method (Build compressed kernel image)```"  ---> "```(X) Build compressed kernel image```"
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/3c7704a7-ea16-4bee-a0bf-6ecd0424f2b7" width="75%">
### 06. ```→ File systems``` => Enable "```<*> Overlay filesystem support```".
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/0cbff894-ba4c-4f51-a1bd-3ffa1963cd51" width="75%">
### 07. ```→ Security options``` => Disable "```Integrity subsystem```" and "```Defex Support```".
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/ca396e53-26fc-4ee4-99ea-c8359926ea51" width="75%">
<hr>

### 08. Exit and Save the config.
- When you see "```configuration written```", stop the compilation process with ```ctrl+c``` and replace the ".config"'s content with your defconfig.
<hr>

### 09. Compile using ```./build.sh``` --> Skip the menuconfig and wait until the compilation finishes..!

Notes : if you encountred errors, you should search these errors in github and find a solution.
<hr>

# How to put the compiled kernel, inside our boot.img..?
### 01. Extract the boot.img from the stock ROM. I prefer https://github.com/ravindu644/Scamsung to do this online.
	- Use exact build number to download the firmware.
### 02. Unpack the boot.img using AIK-Linux which can be found in here : https://github.com/ravindu644/AIK-Linux
- Image : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/d5fee81a-6768-4848-a4a6-37fec6cb355f" width="70%">
### 03. Open the split_img folder and see your kernel uses "```gzip```" compression. If it is, use ```Image.gz```. else use normal "```Image```".
- Kernel without GZIP compression : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/cb1d0ff3-32cb-4d98-9892-5a00d1922680" width="70%">
- Kernel <b>with</b> GZIP compression : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/30ee541e-211c-4a84-971b-f67299ee8793" width="70%">
### 04. If your boot.img has a GZIP kernel, use the "```Image.gz```". Else use "```Image```".
### 05. Rename your compiled kernel to "```boot.img-kernel```" and copy and replace it with the ```boot.img-kernel```, which is in the split_img folder.
### 06. Repack --> rename "image-new.img" to "boot.img" and make a tar file using this command :
```
tar cvf "DEVICE NAME (APatch Support).tar" boot.img
```
### 07. Flash it using Fastboot/ODIN..!
### 08. DONE..!
- Proof : <br><br><img src="https://github.com/ravindu644/APatch/assets/126038496/f0dd204d-e398-4ce1-9897-96e6a51b5673" width="75%">
<hr>

## Written by [@Ravindu_Deshan](https://t.me/Ravindu_Deshan) for [@SamsungTweaks](https://t.me/SamsungTweaks) and [@APatchChannel](https://t.me/APatchChannel) | Sharing this without proper credit is not allowed..❗







  
  
    
  

