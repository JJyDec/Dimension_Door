package controller

import (
	"dimension_door/dao/mysql"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

//和管理员权限相关的

// CheckIsAdminAccount 判断是否是管理员账号
func CheckIsAdminAccount(c *gin.Context)(bool,error){
	openid ,err:= getCurrentUser(c)
	if err != nil {
		zap.L().Error("getCurrentUser(c) failed err : ",zap.Error(err))
		return false,err
	}
	return mysql.CheckIsAdminAccount(openid)
}