package controller

import (
	"dimension_door/logic"
	"github.com/gin-gonic/gin"
	"strconv"
)

//和删除相关的接口

// DeletePostHandle 管理员删除讲座信息
// @Summary 删除讲座信息
// @Description 管理员删除讲座信息
// @Tags 删除
// @Accept application/json
// @Produce application/json
// @Param Authorization header string true "Bearer 用户令牌"
// @Param object query models.ID true "讲座id参数"
// @Security ApiKeyAuth
// @Success 200 {object} _ResponseSuccess
// @Router /delete/:id [get]
func DeletePostHandle(c *gin.Context){
	//查看是否是管理员
	ok , err := CheckIsAdminAccount(c)
	if err != nil {
		ResponseError(c,CodeServerBusy)
		return
	}
	if ok {
		strid := c.Param("id")
		pid,err := strconv.ParseInt(strid,10,64)
		if err != nil {
			ResponseError(c,CodeInvalidParam)
			return
		}
		if err = logic.DeletePostByID(pid);err != nil{
			ResponseError(c,CodeDeleteFailed)
			return
		}
		ResponseSuccess(c,CodeDeleteSuccess,nil)
	}else{
		ResponseError(c,CodeNeedAdmin)
		return
	}

}
