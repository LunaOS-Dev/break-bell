package com.breakbell.app.alarm

enum class AlarmKind(val requestCode: Int) {
    WORK_END(101),
    BREAK_NAG(102),
    BREAK_END(103),
    WORK_HEADS_UP(104),
}
