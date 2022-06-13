package setting

import (
	"fmt"
	"github.com/fsnotify/fsnotify"
	"github.com/spf13/viper"
)
//全局变量用于保存程序的所有配置信息！！！结构体本身就是一个指针
var Conf = new(AppConf)
type AppConf struct {
	Name 			string    			`mapstructure:"name"`
	Mode 			string   			`mapstructure:"mode"`
	Port 			int 				`mapstructure:"port"`
	Version 		string 				`mapstructure:"version"`
	StartTime 		string 				`mapstructure:"start_time"`
	MachineID 		int64  				`mapstructure:"machine_id"`

	*LogConf   							`mapstructure:"log"`
	*MysqlConf 							`mapstructure:"mysql"`
	*RedisConf 							`mapstructure:"redis"`
}

type LogConf struct {
	Level 			string   			`mapstructure:"level"`
	Filename 		string    			`mapstructure:"filename"`
	Max_size 		int   				`mapstructure:"max_size"`
	Max_age 		int    				`mapstructure:"max_age"`
	Max_backups 	int    				`mapstructure:"max_backups"`
}

type MysqlConf struct {
	Host 			string    			`mapstructure:"host"`
	Port 			int   				`mapstructure:"port"`
	User 			string    			`mapstructure:"user"`
	Password  		string   			`mapstructure:"password"`
	Dbname 			string  			`mapstructure:"dbname"`
	Max_conns 		int  				`mapstructure:"max_conns"`
	Max_idle_conns 	int 				`mapstructure:"max_idle_conns"`
}
type RedisConf struct {
	Host 			string    			`mapstructure:"host"`
	Port 			int   				`mapstructure:"port"`
	Db 				int 				`mapstructure:"db"`
	Password 		string    			`mapstructure:"password"`
	Poolsize 		int   				`mapstructure:"poolsize"`
}
//使用viper去获取配置信息
func Init() (err error) {
	//viper.SetConfigFile("./config.yaml")
	viper.SetConfigName("config") //指定配置文件名称（不需要带后缀）
	viper.SetConfigType("yaml") //指定配置文件类型
	viper.AddConfigPath(".")//指定查找配置文件的路径（这里使用相对路径）

	err = viper.ReadInConfig() //读取配置文件信息
	if err != nil {
		fmt.Printf("viper readinconfig failed err :", err)
		return
	}
	//把信息读到结构体中去，反序列化进去
	if err = viper.Unmarshal(Conf);err !=nil{
		fmt.Println("viper unmarshal failed ",err)

	}
	viper.WatchConfig() //检测配置信息是否改变，有改变会重新渲染
	viper.OnConfigChange(func(in fsnotify.Event) {
		if err = viper.Unmarshal(Conf);err !=nil{
			fmt.Println("viper unmarshal failed ",err)
		}
		fmt.Println("config is changed!")
	})
	return
}

