package mysql

import (
	"dimension_door/setting"
	"fmt"
	_ "github.com/go-sql-driver/mysql"
	"github.com/jmoiron/sqlx"
)


var db *sqlx.DB

// Init 初始化MySQL连接
func Init(cfg *setting.MysqlConf) (err error) {
	// "user:password@tcp(host:port)/dbname"
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?parseTime=true&loc=Local", cfg.User, cfg.Password, cfg.Host, cfg.Port, cfg.Dbname)
	db, err = sqlx.Connect("mysql", dsn)
	if err != nil {
		return
	}
	db.SetMaxOpenConns(cfg.Max_conns)//设置最大连接数量
	db.SetMaxIdleConns(cfg.Max_idle_conns)//设置最大空闲连接数
	return
}

// Close 关闭MySQL连接
func Close() {
	_ = db.Close()
}
