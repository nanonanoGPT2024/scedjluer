# Field Mapping: ActivityDetailModal.vue -> Backend Tables

This document maps the input fields in `ActivityDetailModal.vue` to the corresponding columns in the `order_activity` and `order_data_details` tables.

## 1. OrderActivity (`order_activity` table)

| Frontend Model (Vue) | Backend Field (Java) | Database Column | Notes |
| :--- | :--- | :--- | :--- |
| `props.orderId` | `orderId` | `order_id` |  |
| *(Auth Context)* | `agentId` | `agent_id` | Retrieved from session/context |
| `selectedStatus` | `statusId` | `status_id` |  |
| `selectedStatusCall` | `statusCallId` | `status_call_id` |  |
| `selectedResultStatus` | `statusResultId` | `status_result_id` |  |
| `selectedReason` | `reasonId` | `reason_id` |  |
| `remarks` | `remarks` | `remarks` | General remarks |
| `remarks` | `catetan` | `catetan` | Mapped to both `remarks` and `catetan` |
| `datePickUp` | `dateReminder` | `date_reminder` | *To review: currently only handled in OrderDataDetails if Agree, maybe needed here too?* |
| `timePickUp` | `timeReminder` | `time_reminder` | *To review: currently only handled in OrderDataDetails if Agree, maybe needed here too?* |

## 2. OrderDataDetails (`order_data_details` table) - Only if Result is "Agree"

| Frontend Model (Vue) | Backend Field (Java) | Database Column |
| :--- | :--- | :--- |
| `props.orderId` | `orderId` | `order_id` |
| `cardSegment1` | `cardSegment1` | `card_segment_1` |
| `cardSegment2` | `cardSegment2` | `card_segment_2` |
| `fullName` | `suppName1` | `supp_name_1` | *Assumption: mapped to suppName1* |
| `supplementName1` | `suppName1` | `supp_name_1` | *Assumption: uses supplementName1 if exist, or fallback to fullName* |
| `supplementRelation1` | `hubunganSupp1` | `hubungan_supp_1` | |
| `supplementName2` | `suppName2` | `supp_name_2` | |
| `supplementRelation2` | `hubunganSupp2` | `hubungan_supp_2` | |
| `datePickUp` | `pickupDateRemark` | `pickup_date_remark` | |
| `timePickUp` | `pickupTimeRemark` | `pickup_time_remark` | *Converted to integer (HHmm)* |
| `remarkMobileSales` | `remarkForMs` | `remark_for_ms` | |
| `remarkMobileAgent` | `remarksForAgent` | `remarks_for_agent` | |
| `whatsappNo` | `phoneWaRemark` | `phone_wa_remark` | |
| `isLifetime` | `lifetime` | `lifetime` | |
| `landlinePhone` | `landlinePhone` | `landline_phone` | |
| `officePhone` | `officePhone` | `office_phone` | |
| `handphone` | `handphone` | `handphone` | |
| `handphone1` | `handphone1` | `handphone_1` | |
| `handphone2` | `handphone2` | `handphone_2` | |
| `isDomicileMatch` | `domicileCheck` | `domicile_check` | |
| `bioCity` | `kotaKtp` | `kota_ktp` | |
| `bioDistrict` | `kecamatanKtp` | `kecamatan_ktp` | |
| `bioSubdistrict` | `kelurahanKtp` | `kelurahan_ktp` | |
| `bioPostalCode` | `kodePosKtp` | `kode_pos_ktp` | |
| `bioRtRw` | `rtRwKtp` | `rt_rw_ktp` | |
| `domCity` | `kotaDomisili` | `kota_domisili` | |
| `domDistrict` | `kecamatanDomisili` | `kecamatan_domisili` | |
| `domSubdistrict` | `kelurahanDomisili` | `kelurahan_domisili` | |
| `domPostalCode` | `kodePosDomisili` | `kode_pos_domisili` | |
| `domRtRw` | `rtRwDomisili` | `rt_rw_domisili` | |
| `city` | `kotaRemark` | `kota_remark` | |
| `selectedDistrict` | `kecamatanRemark` | `kecamatan_remark` | |
| `selectedSubdistrict` | `kelurahanRemark` | `kelurahan_remark` | |
| `postalCode` | `kodePosRemark` | `kode_pos_remark` | |
| `rtRw` | `rtRwRemark` | `rt_rw_remark` | |
| `idCardNo` | `noKtpKitas` | `no_ktp_kitas` | |
| `taxFileNo` | `noNpwp` | `no_npwp` | |
