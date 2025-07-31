# Ứng dụng Giám sát trẻ em trên điện thoại (Parental Control Application for Mobile Phones)

Ứng dụng này là một giải pháp toàn diện giúp phụ huynh giám sát và bảo vệ con cái khi sử dụng điện thoại di động trên nền tảng Android. Được phát triển để đáp ứng nhu cầu cấp thiết trong bối cảnh các mối nguy hiểm gia tăng đối với trẻ em, cả ngoài đời và trên không gian mạng.

## Mục tiêu chính

* Xây dựng ứng dụng di động cho phép phụ huynh giám sát vị trí và hoạt động sử dụng điện thoại của trẻ.
* Hỗ trợ ngôn ngữ và giao diện thân thiện, phù hợp với người dùng Việt Nam.
* Tăng cường tương tác giữa phụ huynh và trẻ em thông qua các chức năng giao tiếp.
* Đảm bảo an toàn và phát triển toàn diện cho trẻ em.

## Các tính năng nổi bật

Ứng dụng gồm hai phiên bản: một dành cho phụ huynh và một dành cho trẻ em.

* **Theo dõi vị trí thời gian thực:** Giám sát vị trí của trẻ trên bản đồ và thêm các địa điểm an toàn (nhà, trường học).
* **Cảnh báo khẩn cấp (SOS):** Trẻ có thể gửi tín hiệu cầu cứu khẩn cấp đến phụ huynh thông qua nút tiện ích, ô cài đặt nhanh hoặc nhấn liên tục nút nguồn.
* **Giám sát thời gian sử dụng thiết bị:** Phụ huynh có thể xem thời gian sử dụng từng ứng dụng của trẻ.
* **Chặn ứng dụng:** Chặn quyền truy cập của trẻ vào các ứng dụng không phù hợp hoặc khi sử dụng quá nhiều.
* **Quản lý từ khóa nhạy cảm:** Ngăn chặn trẻ tìm kiếm và truy cập nội dung không phù hợp trên mạng.
* **Nghe âm thanh từ xa:** Ghi âm từ xa để phụ huynh biết được môi trường xung quanh trẻ.
* **Nhắn tin hai chiều:** Cho phép phụ huynh và trẻ nhắn tin, gửi file ghi âm, hình ảnh trong phạm vi gia đình.
* **Giao nhiệm vụ:** Phụ huynh có thể tạo nhiệm vụ và lời nhắc, trẻ có thể cập nhật tiến độ hoàn thành.

## Công nghệ sử dụng

* **Android Native & Kotlin:** Nền tảng phát triển chính, sử dụng Kotlin cho hiệu suất và khả năng tương thích cao.
* **Firebase Platform:**
    * **Firebase Realtime Database:** Lưu trữ và đồng bộ dữ liệu thời gian thực (vị trí, tin nhắn, nhiệm vụ).
    * **Firebase Storage:** Lưu trữ các tệp tin (ghi âm, hình ảnh).
    * **Firebase Cloud Messaging (FCM):** Gửi thông báo và cảnh báo khẩn cấp.
    * **Firebase Authentication:** Xác thực và quản lý người dùng.
* **Google Maps SDK:** Hiển thị vị trí của trẻ trên bản đồ.
* **Android Background Task:** Sử dụng Work Manager và Foreground Service để đảm bảo ứng dụng chạy nền ổn định và liên tục.
* **Accessibility Service & UsageStats API:** Hỗ trợ chức năng chặn từ khóa, lọc nội dung và thu thập thời gian sử dụng ứng dụng.
* **Kiến trúc MVVM:** Tăng tính linh hoạt và dễ bảo trì cho hệ thống.

## Một vài hình ảnh minh họa cho ứng dụng

### 1. Theo dõi vị trí realtime

<img src="https://github.com/user-attachments/assets/efde505a-42fa-4f14-9408-9a4662a173f8" alt="Màn hình theo dõi vị trí trẻ em" width="300"/>

### 2. Tạo và giao nhiệm vụ

<img src="https://github.com/user-attachments/assets/666e3b06-1868-43e3-a7f9-de5b1fbb6b5f" alt="Màn hình tạo và giao nhiệm vụ" width="300"/>

### 3. Màn hình quản lý thời gian và chặn ứng dụng

<img src="https://github.com/user-attachments/assets/f53d814e-34e0-42c6-893f-c0b1a274ede1" alt="Màn hình chặn - Bên phía trẻ em" width="300"/> 

<img src="https://github.com/user-attachments/assets/76203c7f-68fc-4b5c-b3ee-ca6780b7a908" alt="Màn hình quản lý thời gian - Bên phía phụ huynh" width="300"/> 

### 4. Màn hình thêm địa điểm thân quen

<img src="https://github.com/user-attachments/assets/0be81c95-3173-4b0e-acd7-25042c619885" alt="Màn hình thêm địa điểm thân quen" width="300"/> 


## Đóng góp nổi bật
* Tính năng cảnh báo khẩn cấp độc đáo và hiệu quả.
* Giải pháp tối ưu kiểm tra vùng an toàn (Two-Stage Filtering & Intelligent Caching) và quản lý thiết bị trẻ em thông minh, cân bằng giữa độ chính xác, hiệu suất và tuổi thọ pin.
