package controller

import (
	"dimension_door/models"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/spf13/viper"
	"go.uber.org/zap"
	"net/http"
)

// WXlogin 向微信服务器发送请求
func WXlogin(code string)(*models.WXLoginResp,error){
	url := "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code"

	//合成url
	url = fmt.Sprintf(url,viper.GetString("appId"),viper.GetString("secret"),code)
	zap.L().Debug("url :",zap.Any("url : ",url))
	//创建http请求
	resp,err := http.Get(url)
	if err != nil {
		return nil,err
	}
	defer resp.Body.Close()

	//解析http请求中的body 数据绑定到我们定义的结构体中
	wxResp := models.WXLoginResp{}
	decoder := json.NewDecoder(resp.Body)
	if err = decoder.Decode(&wxResp);err != nil{
		return nil,err
	}
	zap.L().Debug("errmsg、errcode :",zap.Any("errcode : ",wxResp.Errcode),zap.Any("errmsg : ",wxResp.Errmsg))
	//判断微信接口是否返回了一个异常情况
	if wxResp.Errcode != 0{
		return nil, errors.New(fmt.Sprintf("ErrCode:%s  ErrMsg:%s", wxResp.Errcode, wxResp.Errmsg))
	}
	return &wxResp,nil
}



var ErrorUserNotExist = errors.New("用户未登录")
const CtxUserIDKey = "Openid"

// getCurrentUser 获取当前用户唯一标识（根据上下文GET出来那个key）
func getCurrentUser(c *gin.Context)(openid string,err error){
	uid ,ok  := c.Get(CtxUserIDKey)
	if !ok{
		err = ErrorUserNotExist
		return
	}
	//类型断言，openid是一个空接口
	openid , ok = uid.(string)
	if !ok{
		err = ErrorUserNotExist
		return
	}
	return
}
