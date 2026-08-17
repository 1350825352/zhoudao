package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.dto.ExecuteFlightTaskDTO;
import com.dji.sikong.entity.FlightTask;

public interface FlightTaskService extends IService<FlightTask> {

    FlightTask executeTask(ExecuteFlightTaskDTO dto);

    FlightTask getByTaskUuid(String taskUuid);
}
