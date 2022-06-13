package logic

import (
	"dimension_door/dao/mysql"
	"dimension_door/dao/redis"
	"fmt"
	"go.uber.org/zap"
)



// 与报名相关的逻辑处理

// Application 用户报名讲座
func Application(postid int64,openid string)error{
	//根据openid查看数据库是否有这条消息，有的话返回重复报名错误
	ok ,err := mysql.CheckApplyByID(postid,openid)
	if err != nil {
		//数据库查询错误
		zap.L().Error("mysql.CheckApplyByID(postid,openid) failed:",zap.Error(err))
		return err
	}
	if !ok {
		return mysql.ErrorApplyAgain
	}
	//查找数据库中该帖子有多少人报名
	nums,err := mysql.GetPostedNumsByID(postid)
	if err != nil {
		//数据库查询错误
		zap.L().Error("mysql.GetPostedNumsByID(postid) failed:",zap.Error(err))
		return err
	}
	fmt.Println("nums:",nums)
	//检测是否达到人数上限
	//检查更新post表的已报名人数
	err = mysql.UpdatePostedNums(nums,postid,openid)
	if err != nil {
		if err == mysql.ErrorPostLimit{
			return err
		}
		zap.L().Error("mysql.UpdatePostedNums(nums,postid) failed err :", zap.Error(err))
		return err
	}
	// 插入redis
	redis.ApplyInsertRedis(postid,openid)

	//插入数据库
	//err = mysql.ApplicationInsert(postid,openid) (已经写在上面的更新函数了）
	return err
}

// CancelApplyByID 根据帖子id取消报名
func CancelApplyByID(pid int64 ,openid string)error{
	return mysql.CancelApplyByID(pid,openid)
}