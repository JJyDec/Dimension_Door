package logic

import (
	"dimension_door/dao/mysql"
	"dimension_door/dao/redis"
	"go.uber.org/zap"
	"strconv"
)

//和删除相关的逻辑

// DeletePostByID 根据讲座id删除讲座
func DeletePostByID(pid int64)error{
	err :=  mysql.DeletePostByID(pid)
	if err != nil {
		zap.L().Error("mysql.DeletePostByID(pid,tid) failed err: ",zap.Error(err))
		return err
	}
	err =  redis.DeletePostByID(strconv.Itoa(int(pid)))
	if err != nil {
		zap.L().Error("redis.DeletePostByID(strconv.Itoa(int(pid))) failed err: ",zap.Error(err))
		return err
	}
	return err
}
