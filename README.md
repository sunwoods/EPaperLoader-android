# EPaperLoader-android
 ePaper loader source files for esp32  

写在前面： 本人代码能力平平，项目仅供参考，欢迎指正

***
这是 __`墨水屏ESP32无线图传`__ 的安卓程序部分  
基于微雪电子（waveshare）开源程序的二次开发，加入了一些新的功能：  
1. 新增几种屏幕面板的支持（参见下方说明）
2. 支持蓝牙BLE传图（微雪官方程序支持蓝牙SPP），需使用配套的ESP32固件
3. 支持蓝牙模式下的压缩方式传图，本身BLE速度很慢，这样可以提高效率
4. 支持wifi传图，需使用配套的ESP32固件，连接蓝牙后会自动同步IP信息
5. 支持从APP上配置ESP32单板的SPI管脚编号，适配不同的硬件接线方式
6. 将原微雪官方示例中引用的第三方图片处理库android-image-cropper:2.8.+（已失效）升级为android-image-cropper:4.6.0

***
新增支持的几种屏幕面板包括（沿用微雪命名规则）： 
* [7.3 inch e-Paper(F)](https://www.waveshare.net/wiki/7.3inch_e-Paper_HAT_(F) "分辨率：800x480") 
* [12.48 inch e-Paper (B)](https://www.waveshare.net/wiki/12.48inch_e-Paper_Module_(B) "分辨率：1304x984")
* 9.7 inch e-Paper (G)
* 9.69 inch e-Paper (B)
* [7.3 inch e-Paper (E)](https://www.waveshare.net/wiki/7.3inch_e-Paper_HAT_(E) "分辨率：800x480")
* 4.37 inch e-Paper (B)
* 1.54 inch e-Paper V2
* 10.2 inch e-Paper (B)
* 6.0 inch Parallel

***
注意事项：
* 因为加入了wifi批量数据传输和蓝牙压缩/解压缩功能，所以需要使用带有PSRAM的ESP32单板，否则不能正常运行
* 12.48b和9.69b因为分别需要使用14根和7根数据线，与其他SPI标准的6根数据线屏幕不兼容，所以SPI管脚的定义在固件代码.h中单独定义，请特别注意
* 项目提供了已经打包好的[安卓APP试用包](https://github.com/sunwoods/EPaperLoader-android/blob/main/apk-release/EPaperUploader.apk)（需使用配套的ESP32固件） 

***
附： APP界面预览  
<img src="https://github.com/sunwoods/EPaperLoader-android/blob/main/readme-img/img-01.jpg" width="300"/>

墨水屏刷新效果  
<img src="https://github.com/sunwoods/EPaperLoader-android/blob/main/readme-img/img-02.jpg" width="300"/>

并口屏刷新效果  
<img src="https://github.com/sunwoods/EPaperLoader-android/blob/main/readme-img/img-03.jpg" width="300"/>
